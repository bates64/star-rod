package game.map.impex;

import static org.lwjgl.assimp.Assimp.*;

import java.io.File;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.AIColor4D;
import org.lwjgl.assimp.AIFace;
import org.lwjgl.assimp.AIMaterial;
import org.lwjgl.assimp.AIMesh;
import org.lwjgl.assimp.AIScene;
import org.lwjgl.assimp.AIString;
import org.lwjgl.assimp.AIVector3D;

import app.StarRodException;
import game.map.MapObject.HitType;
import game.map.hit.Collider;
import game.map.hit.Zone;
import game.map.mesh.AbstractMesh;
import game.map.mesh.TexturedMesh;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.shape.Model;
import game.map.shape.TriangleBatch;
import game.map.shape.UV;
import game.texture.ModelTexture;
import util.Logger;

public abstract class AssimpImporter
{
	public static class AssimpImportOptions
	{
		boolean triangulate = true;
		boolean joinVertices = true;
		boolean convertZupToYup = true;

		float uvScale = 1024.0f;
	}

	public static List<Model> importModels(File modelFile, AssimpImportOptions options)
	{
		List<Model> models = new ArrayList<>();

		AIScene aiScene = aiImportFile(modelFile.getPath(), 0); //aiProcess_JoinIdenticalVertices);
		if (aiScene == null)
			throw new StarRodException("Error loading " + modelFile.getName());

		int numMaterials = aiScene.mNumMaterials();
		PointerBuffer aiMaterials = aiScene.mMaterials();

		List<String> texNames = new ArrayList<>();
		for (int i = 0; i < numMaterials; i++) {
			AIMaterial aiMaterial = AIMaterial.create(aiMaterials.get(i));
			AIString path = AIString.calloc();
			aiGetMaterialTexture(aiMaterial, aiTextureType_DIFFUSE, 0, path, (IntBuffer) null, null, null, null, null, null);
			texNames.add(FilenameUtils.getBaseName(path.dataString()));
			Logger.log("Found material: " + path.dataString());
		}

		int numMeshes = aiScene.mNumMeshes();
		PointerBuffer aiMeshes = aiScene.mMeshes();

		for (int i = 0; i < numMeshes; i++) {
			AIMesh aiMesh = AIMesh.create(aiMeshes.get(i));
			int matIndex = aiMesh.mMaterialIndex();

			Model model = Model.createBareModel();
			model.setName(aiMesh.mName().dataString());

			if (matIndex >= 0 && matIndex < texNames.size())
				model.getMesh().setTexture(texNames.get(matIndex));

			TriangleBatch batch = processMesh(aiMesh, model.getMesh(), options);
			model.getMesh().displayListModel.addElement(batch);

			model.updateMeshHierarchy();
			models.add(model);
		}

		return models;
	}

	public static List<Collider> importColliders(File modelFile, AssimpImportOptions options)
	{
		List<Collider> colliders = new ArrayList<>();

		AIScene aiScene = aiImportFile(modelFile.getPath(), 0);
		if (aiScene == null)
			throw new StarRodException("Error loading " + modelFile.getName());

		int numMeshes = aiScene.mNumMeshes();
		PointerBuffer aiMeshes = aiScene.mMeshes();

		for (int i = 0; i < numMeshes; i++) {
			AIMesh aiMesh = AIMesh.create(aiMeshes.get(i));

			Collider c = new Collider(HitType.HIT);
			c.setName(aiMesh.mName().dataString());
			c.mesh.batch = processMesh(aiMesh, c.mesh, options);

			c.updateMeshHierarchy();
			colliders.add(c);
		}

		return colliders;
	}

	public static List<Zone> importZones(File modelFile, AssimpImportOptions options)
	{
		List<Zone> zones = new ArrayList<>();

		AIScene aiScene = aiImportFile(modelFile.getPath(), 0);
		if (aiScene == null)
			throw new StarRodException("Error loading " + modelFile.getName());

		int numMeshes = aiScene.mNumMeshes();
		PointerBuffer aiMeshes = aiScene.mMeshes();

		for (int i = 0; i < numMeshes; i++) {
			AIMesh aiMesh = AIMesh.create(aiMeshes.get(i));

			Zone z = new Zone(HitType.HIT);
			z.setName(aiMesh.mName().dataString());
			z.mesh.batch = processMesh(aiMesh, z.mesh, options);

			z.updateMeshHierarchy();
			zones.add(z);
		}

		return zones;
	}

	private static TriangleBatch processMesh(AIMesh aiMesh, AbstractMesh srMesh, AssimpImportOptions options)
	{
		TriangleBatch batch = new TriangleBatch(srMesh);

		ArrayList<Vertex> vertices = new ArrayList<>();
		HashMap<Vertex, Vertex> vtxMap = new HashMap<>();

		float uScale = options.uvScale;
		float vScale = options.uvScale;
		if (srMesh instanceof TexturedMesh texMesh) {
			if (texMesh.texture != null) {
				uScale = ModelTexture.getScaleU(texMesh.texture);
				vScale = ModelTexture.getScaleV(texMesh.texture);
			}
		}

		AIVector3D.Buffer aiVertices = aiMesh.mVertices();
		while (aiVertices.hasRemaining()) {
			AIVector3D aiVertex = aiVertices.get();
			Vertex vtx;
			if (options.convertZupToYup)
				vtx = new Vertex(aiVertex.x(), aiVertex.z(), aiVertex.y());
			else
				vtx = new Vertex(aiVertex.x(), aiVertex.y(), aiVertex.z());
			vertices.add(vtx);
			vtxMap.put(vtx, vtx);
		}

		AIVector3D.Buffer texCoords = aiMesh.mTextureCoords(0);
		int numTexCoords = texCoords != null ? texCoords.remaining() : 0;
		for (int i = 0; i < Math.min(numTexCoords, vertices.size()); i++) {
			AIVector3D texCoord = texCoords.get();
			vertices.get(i).uv = new UV(Math.round(uScale * texCoord.x()), Math.round(vScale * (1.0f - texCoord.y())));
		}

		AIColor4D.Buffer colors = aiMesh.mColors(0);
		int numColors = colors != null ? colors.remaining() : 0;
		for (int i = 0; i < numColors; i++) {
			Vertex vtx;
			if (options.joinVertices)
				vtx = vtxMap.get(vertices.get(i));
			else
				vtx = vertices.get(i);
			vtx.r = Math.round(256 * colors.r());
			vtx.g = Math.round(256 * colors.g());
			vtx.b = Math.round(256 * colors.b());
			vtx.a = Math.round(256 * colors.a());
		}

		AIFace.Buffer aiFaces = aiMesh.mFaces();
		for (int i = 0; i < aiMesh.mNumFaces(); i++) {
			AIFace aiFace = aiFaces.get(i);
			IntBuffer buffer = aiFace.mIndices();
			List<Integer> indices = new ArrayList<>();
			while (buffer.hasRemaining())
				indices.add(buffer.get());

			int maxIndex = options.triangulate ? indices.size() : 3;
			for (int j = 2; j < maxIndex; j++) {
				Triangle t = new Triangle(
					vertices.get(indices.get(0)),
					vertices.get(indices.get(j)),
					vertices.get(indices.get(j - 1)));
				batch.triangles.add(t);
			}
		}

		Logger.logf("Imported mesh with %d vertices and %d faces.", vertices.size(), batch.triangles.size());
		return batch;
	}
}
