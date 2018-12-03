/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2018 Alex Herbert
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package uk.ac.sussex.gdsc.foci.gui;

import uk.ac.sussex.gdsc.foci.controller.OptimiserController;
import uk.ac.sussex.gdsc.foci.converter.ValidImagesConverter;
import uk.ac.sussex.gdsc.foci.model.FindFociModel;

import ij.macro.MacroRunner;

import org.jdesktop.beansbinding.AutoBinding;
import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.Bindings;
import org.jdesktop.swingbinding.JComboBoxBinding;
import org.jdesktop.swingbinding.SwingBindings;

import java.awt.EventQueue;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * Provides a permanent form front-end for the {@link uk.ac.sussex.gdsc.foci.FindFoci} algorithm.
 */
public class OptimiserView extends JFrame {
  private static final long serialVersionUID = -3283971398975124411L;

  // Flags used to control the enabled status of the run button.
  // The button should be enabled when there are images in the list.
  private boolean runEnabled = false;

  private FindFociModel model;
  private OptimiserController controller;

  private JPanel contentPane;
  private JLabel lblImage;
  private JComboBox<String> comboImageList;
  private JButton btnRun;
  private JPanel panel;
  private JButton btnHelp;

  /**
   * Launch the application.
   *
   * @param args the arguments
   */
  public static void main(String[] args) {
    EventQueue.invokeLater(new Runnable() {
      @Override
      public void run() {
        try {
          final OptimiserView frame = new OptimiserView();
          frame.setVisible(true);
        } catch (final Exception ex) {
          ex.printStackTrace();
        }
      }
    });
  }

  /**
   * Create the frame.
   */
  public OptimiserView() {
    init();
  }

  private void init() {
    model = new FindFociModel();
    controller = new OptimiserController(model);

    addWindowListener(new WindowAdapter() {
      @Override
      public void windowActivated(WindowEvent e) {
        controller.updateImageList();
      }
    });

    setTitle("FindFoci Optimiser");
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setBounds(100, 100, 450, 105);
    contentPane = new JPanel();
    contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
    setContentPane(contentPane);
    final GridBagLayout gbl_contentPane = new GridBagLayout();
    gbl_contentPane.columnWidths = new int[] {62, 0, 0};
    gbl_contentPane.rowHeights = new int[] {0, 0, 0};
    gbl_contentPane.columnWeights = new double[] {0.0, 1.0, Double.MIN_VALUE};
    gbl_contentPane.rowWeights = new double[] {0.0, 1.0, Double.MIN_VALUE};
    contentPane.setLayout(gbl_contentPane);

    lblImage = new JLabel("Image");
    final GridBagConstraints gbc_lblImage = new GridBagConstraints();
    gbc_lblImage.anchor = GridBagConstraints.EAST;
    gbc_lblImage.insets = new Insets(0, 0, 5, 5);
    gbc_lblImage.gridx = 0;
    gbc_lblImage.gridy = 0;
    contentPane.add(lblImage, gbc_lblImage);

    comboImageList = new JComboBox<>();
    comboImageList.setToolTipText("Select the input image");
    comboImageList.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        comboImageList.firePropertyChange("selectedItem", 0, 1);
      }
    });
    comboImageList.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        controller.updateImageList();
      }
    });
    final GridBagConstraints gbc_comboBox = new GridBagConstraints();
    gbc_comboBox.insets = new Insets(0, 0, 5, 0);
    gbc_comboBox.fill = GridBagConstraints.HORIZONTAL;
    gbc_comboBox.gridx = 1;
    gbc_comboBox.gridy = 0;
    contentPane.add(comboImageList, gbc_comboBox);

    panel = new JPanel();
    final GridBagConstraints gbc_panel = new GridBagConstraints();
    gbc_panel.gridwidth = 2;
    gbc_panel.fill = GridBagConstraints.BOTH;
    gbc_panel.gridx = 0;
    gbc_panel.gridy = 1;
    contentPane.add(panel, gbc_panel);

    btnHelp = new JButton("Help");
    btnHelp.addMouseListener(new MouseAdapter() {
      @SuppressWarnings("unused")
      @Override
      public void mouseClicked(MouseEvent e) {
        final String macro = "run('URL...', 'url=" + uk.ac.sussex.gdsc.help.URL.FIND_FOCI + "');";
        new MacroRunner(macro);
      }
    });
    panel.add(btnHelp);

    btnRun = new JButton("Run");
    panel.add(btnRun);
    btnRun.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        // Run in a new thread to allow updates to the IJ progress bar
        final Thread thread = new Thread(controller);
        thread.start();
      }
    });
    initDataBindings();
  }

  /**
   * @param runEnabled the runEnabled to set
   */
  public void setRunEnabled(boolean runEnabled) {
    final boolean oldValue = this.runEnabled;
    this.runEnabled = runEnabled;
    this.firePropertyChange("runEnabled", oldValue, runEnabled);
  }

  /**
   * @return the runEnabled.
   */
  public boolean isRunEnabled() {
    return runEnabled;
  }

  /**
   * Inits the data bindings.
   */
  @SuppressWarnings("rawtypes")
  protected void initDataBindings() {
    final BeanProperty<FindFociModel, List<String>> findFociModelBeanProperty =
        BeanProperty.create("imageList");
    final JComboBoxBinding<String, FindFociModel, JComboBox> jComboBinding =
        SwingBindings.createJComboBoxBinding(UpdateStrategy.READ, model, findFociModelBeanProperty,
            comboImageList);
    jComboBinding.bind();
    //
    final BeanProperty<FindFociModel, String> findFociModelBeanProperty_1 =
        BeanProperty.create("selectedImage");
    final BeanProperty<JComboBox, Object> jComboBoxBeanProperty =
        BeanProperty.create("selectedItem");
    final AutoBinding<FindFociModel, String, JComboBox, Object> autoBinding =
        Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociModelBeanProperty_1,
            comboImageList, jComboBoxBeanProperty);
    autoBinding.bind();
    //
    final BeanProperty<JButton, Boolean> jButtonBeanProperty = BeanProperty.create("enabled");
    final AutoBinding<FindFociModel, List<String>, JButton, Boolean> autoBinding_1 =
        Bindings.createAutoBinding(UpdateStrategy.READ, model, findFociModelBeanProperty, btnRun,
            jButtonBeanProperty);
    autoBinding_1.setConverter(new ValidImagesConverter());
    autoBinding_1.bind();
  }
}
