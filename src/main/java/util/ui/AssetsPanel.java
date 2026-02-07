package util.ui;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.UIManager;

import assets.AssetManager;
import assets.ui.MapAsset;
import assets.AssetHandle;
import net.miginfocom.swing.MigLayout;
import util.Logger;

public class AssetsPanel extends JPanel {
  private Collection<? extends AssetHandle> results;

  private JPanel resultsPanel;

  public AssetsPanel() {
    super();

    try {
      results = AssetManager.getMapSources().stream().map(asset -> new MapAsset(asset)).toList();
    } catch (Exception e) {
      Logger.logError("Failed to load assets: " + e.getMessage());
      results = new ArrayList<>();
    }

    setLayout(new MigLayout("ins 4"));

    resultsPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 0, 0));
    for (AssetHandle asset : results) {
      //resultsPanel.add(createAssetItem(asset));
    }

    JScrollPane scrollPane = new JScrollPane(resultsPanel);
    scrollPane.setBorder(null);

    add(scrollPane, "grow, push");
  }

  private JPanel createAssetItem(AssetHandle asset) {
    JPanel panel = new JPanel(new MigLayout("ins 0, fill"));

    panel.setPreferredSize(new Dimension(80, 80));
    panel.setOpaque(true);

    JLabel icon = new JLabel(ThemedIcon.PACKAGE_24);
    // TODO: worker to load thumbnail with an asset method (e.g. MapAsset can return the thumbnail made by action_captureThumbnails)

    JLabel name = new JLabel(asset.getAssetName());
    name.setPreferredSize(new Dimension(80, 20));

    panel.add(icon, "wrap");
    panel.add(name, "align center, wmax 80");

    String desc = asset.getAssetDescription();
    if (desc != null && !desc.isEmpty()) {
      panel.setToolTipText(desc);
    }

    panel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent e) {
        panel.setBackground(UIManager.getColor("Table.selectionBackground"));
      }

      @Override
      public void mouseExited(MouseEvent e) {
        panel.setBackground(null);
      }

      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
          // Double-click to open
          openAsset(asset);
        }
      }
    });

    return panel;
  }

  private void openAsset(AssetHandle asset) {
    // TODO
  }
}
