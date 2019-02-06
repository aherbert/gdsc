/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2019 Alex Herbert
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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;

/**
 * Dialog that can be used to reconfigure the minimum and maximum limits for a JSlider.
 */
public class SliderLimitHelper extends JDialog implements ActionListener {
  private static final long serialVersionUID = 2830394230842354064L;

  private final JPanel contentPanel = new JPanel();
  private JLabel lblParametername;
  private JFormattedTextField txtMinimum;
  private JFormattedTextField txtMaximum;
  private boolean oked;
  private double lowerBound = Double.NEGATIVE_INFINITY;
  private double upperBound = Double.POSITIVE_INFINITY;

  /**
   * Launch the application.
   *
   * @param args the arguments
   */
  public static void main(String[] args) {
    try {
      final SliderLimitHelper dialog = new SliderLimitHelper();
      dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
      dialog.setVisible(true);
    } catch (final Exception ex) {
      Logger.getLogger(SliderLimitHelper.class.getName()).log(Level.SEVERE,
          "Error showing the dialog", ex);
    }
  }

  /**
   * Instantiates a new slider limit helper.
   *
   * @param parent the parent
   */
  public SliderLimitHelper(Component parent) {
    init();
    setLocationRelativeTo(parent);
  }

  /**
   * Create the dialog.
   */
  public SliderLimitHelper() {
    addWindowListener(new WindowAdapter() {
      @Override
      public void windowActivated(WindowEvent event) {
        validMin();
        validMax();
      }
    });
    init();
  }

  /**
   * Inits the.
   */
  private void init() {
    setModalityType(ModalityType.DOCUMENT_MODAL);
    setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

    setBounds(100, 100, 197, 145);
    getContentPane().setLayout(new BorderLayout());
    contentPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
    getContentPane().add(contentPanel, BorderLayout.CENTER);
    final GridBagLayout gridBagLayout = new GridBagLayout();
    gridBagLayout.columnWidths = new int[] {0, 0, 0};
    gridBagLayout.rowHeights = new int[] {0, 0, 0, 0};
    gridBagLayout.columnWeights = new double[] {0.0, 1.0, Double.MIN_VALUE};
    gridBagLayout.rowWeights = new double[] {0.0, 0.0, 0.0, Double.MIN_VALUE};
    contentPanel.setLayout(gridBagLayout);
    {
      lblParametername = new JLabel("ParameterName");
      final GridBagConstraints gbc_lblParametername = new GridBagConstraints();
      gbc_lblParametername.anchor = GridBagConstraints.WEST;
      gbc_lblParametername.gridwidth = 2;
      gbc_lblParametername.insets = new Insets(0, 0, 5, 5);
      gbc_lblParametername.gridx = 0;
      gbc_lblParametername.gridy = 0;
      contentPanel.add(lblParametername, gbc_lblParametername);
    }
    {
      final JLabel lblMinimum = new JLabel("Minimum");
      final GridBagConstraints gbc_lblMinimum = new GridBagConstraints();
      gbc_lblMinimum.anchor = GridBagConstraints.EAST;
      gbc_lblMinimum.insets = new Insets(0, 0, 5, 5);
      gbc_lblMinimum.gridx = 0;
      gbc_lblMinimum.gridy = 1;
      contentPanel.add(lblMinimum, gbc_lblMinimum);
    }
    {
      txtMinimum = new JFormattedTextField();
      txtMinimum.addPropertyChangeListener(new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
          if (evt.getPropertyName().equals("textFormatter")) {
            validMin();
          }
        }
      });
      txtMinimum.addKeyListener(new KeyAdapter() {
        @Override
        public void keyReleased(KeyEvent evt) {
          validMin();
        }
      });
      txtMinimum.setValue(Double.valueOf(0));
      final GridBagConstraints gbc_txtMinimum = new GridBagConstraints();
      gbc_txtMinimum.insets = new Insets(0, 0, 5, 0);
      gbc_txtMinimum.fill = GridBagConstraints.HORIZONTAL;
      gbc_txtMinimum.gridx = 1;
      gbc_txtMinimum.gridy = 1;
      contentPanel.add(txtMinimum, gbc_txtMinimum);
    }
    {
      final JLabel lblMaximum = new JLabel("Maximum");
      final GridBagConstraints gbc_lblMaximum = new GridBagConstraints();
      gbc_lblMaximum.anchor = GridBagConstraints.EAST;
      gbc_lblMaximum.insets = new Insets(0, 0, 0, 5);
      gbc_lblMaximum.gridx = 0;
      gbc_lblMaximum.gridy = 2;
      contentPanel.add(lblMaximum, gbc_lblMaximum);
    }
    {
      txtMaximum = new JFormattedTextField();
      txtMaximum.addPropertyChangeListener(new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
          if (evt.getPropertyName().equals("textFormatter")) {
            validMax();
          }
        }
      });
      txtMaximum.addKeyListener(new KeyAdapter() {
        @Override
        public void keyReleased(KeyEvent evt) {
          validMax();
        }
      });
      txtMaximum.setValue(new Double(10));
      final GridBagConstraints gbc_txtMaximum = new GridBagConstraints();
      gbc_txtMaximum.fill = GridBagConstraints.HORIZONTAL;
      gbc_txtMaximum.gridx = 1;
      gbc_txtMaximum.gridy = 2;
      contentPanel.add(txtMaximum, gbc_txtMaximum);
    }
    {
      final JPanel buttonPane = new JPanel();
      buttonPane.setLayout(new FlowLayout(FlowLayout.RIGHT));
      getContentPane().add(buttonPane, BorderLayout.SOUTH);
      {
        final JButton okButton = new JButton("OK");
        okButton.addActionListener(this);
        okButton.setActionCommand("OK");
        buttonPane.add(okButton);
        getRootPane().setDefaultButton(okButton);
      }
      {
        final JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(this);
        cancelButton.setActionCommand("Cancel");
        buttonPane.add(cancelButton);
      }
    }
  }

  /**
   * Sets the parameter name.
   *
   * @param name The parameter name
   */
  public void setParameterName(String name) {
    this.lblParametername.setText(name);
  }

  /**
   * Sets the minimum.
   *
   * @param minimum the minimum to set
   */
  public void setMinimum(double minimum) {
    this.txtMinimum.setValue(minimum);
  }

  /**
   * Gets the minimum.
   *
   * @return the minimum.
   */
  public double getMinimum() {
    return (Double) txtMinimum.getValue();
  }

  /**
   * Sets the maximum.
   *
   * @param maximum the maximum to set
   */
  public void setMaximum(double maximum) {
    this.txtMaximum.setValue(maximum);
  }

  /**
   * Gets the maximum.
   *
   * @return the maximum.
   */
  public double getMaximum() {
    return (Double) txtMaximum.getValue();
  }

  /** {@inheritDoc} */
  @Override
  public void actionPerformed(ActionEvent event) {
    if ("OK".equals(event.getActionCommand())) {
      if (valid()) {
        oked = true;
        dispose();
      }
      return;
    }
    if ("Cancel".equals(event.getActionCommand())) {
      dispose();
    }
  }

  /**
   * Check if the min and max fields are valid.
   *
   * @return true, if successful
   */
  private boolean valid() {
    if (!validMin()) {
      return false;
    }
    if (!validMax()) {
      return false;
    }
    final double min = getMinimum();
    final double max = getMaximum();
    if (max <= min) {
      // Invert for convenience
      setMinimum(max);
      setMaximum(min);
    }
    return true;
  }

  /**
   * Check if the min field is valid.
   *
   * @return true, if successful
   */
  private boolean validMin() {
    return validField(txtMinimum);
  }

  /**
   * Check if the max field is valid.
   *
   * @return true, if successful
   */
  private boolean validMax() {
    return validField(txtMaximum);
  }

  /**
   * Check if the field is valid.
   *
   * @param txtField the txt field
   * @return true, if successful
   */
  private boolean validField(JFormattedTextField txtField) {
    if (isEmpty(txtField)) {
      return true;
    }

    try {
      final double value = Double.parseDouble(txtField.getText());
      if (value >= lowerBound && value <= upperBound) {
        txtField.setForeground(Color.BLACK);
        return true;
      }
    } catch (final NumberFormatException ex) {
      // Ignore
    }
    txtField.setForeground(Color.RED);
    return false;
  }

  /**
   * Checks if is empty.
   *
   * @param txtField the txt field
   * @return true, if is empty
   */
  private static boolean isEmpty(JFormattedTextField txtField) {
    if (txtField.getText() == null || txtField.getText().equals("")) {
      txtField.setForeground(Color.BLACK);
      return true;
    }
    return false;
  }

  /**
   * Gets if the dialog was Oked..
   *
   * @return true if the dialog was Oked.
   */
  public boolean getOked() {
    return oked;
  }

  /**
   * Sets the lower bound.
   *
   * @param lowerBound the new lower bound
   */
  public void setLowerBound(double lowerBound) {
    this.lowerBound = lowerBound;
    String tooltip = null;
    if (lowerBound != Double.NEGATIVE_INFINITY) {
      tooltip = "Lower bound = " + lowerBound;
    }
    this.txtMinimum.setToolTipText(tooltip);
  }

  /**
   * Gets the lower bound.
   *
   * @return the lower bound
   */
  public double getLowerBound() {
    return lowerBound;
  }

  /**
   * Sets the upper bound.
   *
   * @param upperBound the new upper bound
   */
  public void setUpperBound(double upperBound) {
    this.upperBound = upperBound;
    String tooltip = null;
    if (upperBound != Double.POSITIVE_INFINITY) {
      tooltip = "Upper bound = " + upperBound;
    }
    this.txtMaximum.setToolTipText(tooltip);
  }

  /**
   * Gets the upper bound.
   *
   * @return the upper bound
   */
  public double getUpperBound() {
    return upperBound;
  }

  /**
   * Updates the minimum/maximum for a JSlider. Presents a dialog where the user can configure the
   * limits using text input. The limits will be multiplied by the scale factor before setting the
   * slider limits (allowing fractional input to be modelled with the integer scale on the slider).
   *
   * @param slider the slider
   * @param title The title of the dialog
   * @param scaleFactor The scalefactor applied to the input text to set the slider limits
   * @return true if the limits were updated
   */
  public static boolean updateRangeLimits(JSlider slider, String title, double scaleFactor) {
    return updateRangeLimits(slider, title, scaleFactor, Double.NEGATIVE_INFINITY,
        Double.POSITIVE_INFINITY);
  }

  /**
   * Updates the minimum/maximum for a JSlider. Presents a dialog where the user can configure the
   * limits using text input. The limits will be multiplied by the scale factor before setting the
   * slider limits (allowing fractional input to be modelled with the integer scale on the slider).
   *
   * @param slider the slider
   * @param title The title of the dialog
   * @param scaleFactor The scale factor applied to the input text to set the slider limits
   * @param lowerBound Lower bound for the range
   * @param upperBound Upper bound for the range
   * @return true if the limits were updated
   */
  public static boolean updateRangeLimits(JSlider slider, String title, double scaleFactor,
      double lowerBound, double upperBound) {
    final SliderLimitHelper dialog = new SliderLimitHelper(slider);
    dialog.setTitle(title);
    dialog.setParameterName(title);
    dialog.setMinimum(slider.getMinimum() / scaleFactor);
    dialog.setMaximum(slider.getMaximum() / scaleFactor);
    dialog.setLowerBound(lowerBound);
    dialog.setUpperBound(upperBound);
    dialog.setVisible(true);

    if (dialog.getOked()) {
      slider.setMinimum((int) (dialog.getMinimum() * scaleFactor));
      slider.setMaximum((int) (dialog.getMaximum() * scaleFactor));
      return true;
    }

    return false;
  }
}
