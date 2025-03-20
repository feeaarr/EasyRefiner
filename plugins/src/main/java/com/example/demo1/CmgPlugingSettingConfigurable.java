package com.example.demo1;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

public class CmgPlugingSettingConfigurable implements Configurable {
    private JPanel myMainPanel;
    private JComboBox<String> modelTypeComboBox; // 下拉选择
    private JTextField temperatureTextField;
    private JSlider temperatureSlider;
    private JTextField maxTokenTextField;
    private JTextField model_nameTextField;
    private JTextField api_keyTextField;
    private JTextField base_urlTextField;

    public @NlsContexts.ConfigurableName String getDisplayName() {
        return "EasyRefiner Settings";
    }

    @Override
    public @Nullable JComponent createComponent() {
        myMainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 创建下拉选择框，包含llm和ft两个选项
        String[] modelOptions = {"llm", "ft"};

        modelTypeComboBox = new ComboBox<>(modelOptions);
        modelTypeComboBox.setSelectedItem(MyPluginSettings.getInstance().getState().modelType); // 设置默认选项

        // 创建滑动条
        double initialTemperature = Double.parseDouble(MyPluginSettings.getInstance().getState().temperature);
        int temperatureValue = (int) (initialTemperature * 10);
        temperatureSlider = new JSlider(0, 20, temperatureValue);
        temperatureSlider.addChangeListener(e -> {
            double value = temperatureSlider.getValue() / 10.0;
            temperatureTextField.setText(String.valueOf(value));
        });

        JLabel modelTypeLabel = new JLabel("Method_Options");
        JLabel temperatureLabel = new JLabel("Select Temperature");
        JLabel maxTokenLabel = new JLabel("The maximum number of output tokens");
        JLabel model_nameLabel = new JLabel("Model_Name");
        JLabel api_keyLabel = new JLabel("API_KEY");
        JLabel base_urlLabel = new JLabel("BASE_URL");


        temperatureTextField = new JTextField(String.valueOf(MyPluginSettings.getInstance().getState().temperature), 10);
        temperatureTextField.setEnabled(false);
        maxTokenTextField = new JTextField(String.valueOf(MyPluginSettings.getInstance().getState().maxToken), 10);
        model_nameTextField = new JTextField(String.valueOf(MyPluginSettings.getInstance().getState().model_name), 10);
        api_keyTextField = new JTextField(String.valueOf(MyPluginSettings.getInstance().getState().api_key), 10);
        base_urlTextField = new JTextField(String.valueOf(MyPluginSettings.getInstance().getState().base_url), 10);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        myMainPanel.add(modelTypeLabel, gbc);

        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        myMainPanel.add(modelTypeComboBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        myMainPanel.add(temperatureLabel, gbc);

        gbc.gridx = 2;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        myMainPanel.add(temperatureSlider, gbc);

        gbc.gridx = 4;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        myMainPanel.add(temperatureTextField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        myMainPanel.add(maxTokenLabel, gbc);

        gbc.gridx = 2;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        myMainPanel.add(maxTokenTextField, gbc);

        gbc.gridx = 4;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        myMainPanel.add(new JLabel("word"), gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        myMainPanel.add(model_nameLabel, gbc);



        gbc.gridx =2;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        myMainPanel.add(model_nameTextField, gbc);


        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        myMainPanel.add(api_keyLabel, gbc);

        gbc.gridx = 2;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        myMainPanel.add(api_keyTextField, gbc);


        gbc.gridx =0;
        gbc.gridy = 5;
        gbc.gridwidth = 2;
        myMainPanel.add(base_urlLabel, gbc);

        gbc.gridx = 2;
        gbc.gridy = 5;
        gbc.gridwidth = 2;
        myMainPanel.add(base_urlTextField, gbc);



        return myMainPanel;
    }

    @Override
    public boolean isModified() {
        String selectedModelType = (String) modelTypeComboBox.getSelectedItem();
        boolean modified = !Objects.equals(selectedModelType, MyPluginSettings.getInstance().getState().modelType);
        modified |= temperatureSlider.getValue() / 10.0 != Double.parseDouble(MyPluginSettings.getInstance().getState().temperature);
        String modifiedMaxTokens = (String) maxTokenTextField.getText();
        String modifiedModelName = (String) model_nameTextField.getText();
        String modifiedAPIKEY = (String) api_keyTextField.getText();
        String modifiedBaseUrl = (String) base_urlTextField.getText();
        boolean isModifiedMaxTokens = !MyPluginSettings.getInstance().getState().maxToken.equals(modifiedMaxTokens);
        boolean isModifiedModelName = !MyPluginSettings.getInstance().getState().model_name.equals(modifiedModelName);
        boolean isModifiedAPIKEY = !MyPluginSettings.getInstance().getState().api_key.equals(modifiedAPIKEY);
        boolean isModifiedBaseUrl = !MyPluginSettings.getInstance().getState().base_url.equals(modifiedBaseUrl);
        modified |= (isModifiedMaxTokens | isModifiedModelName | isModifiedAPIKEY | isModifiedBaseUrl);
        return modified;
    }

    @Override
    public void apply() throws ConfigurationException {
        MyPluginSettings.getInstance().getState().modelType = (String) modelTypeComboBox.getSelectedItem();
        MyPluginSettings.getInstance().getState().temperature = temperatureTextField.getText();
        MyPluginSettings.getInstance().getState().maxToken = maxTokenTextField.getText();
        MyPluginSettings.getInstance().getState().model_name = model_nameTextField.getText();
        MyPluginSettings.getInstance().getState().api_key = api_keyTextField.getText();
        MyPluginSettings.getInstance().getState().base_url = base_urlTextField.getText();
    }

    @Override
    public void reset() {
        modelTypeComboBox.setSelectedItem(MyPluginSettings.getInstance().getState().modelType);
        String temperature = MyPluginSettings.getInstance().getState().temperature;
        temperatureTextField.setText(temperature);
        int temperatureValue = (int) (Double.parseDouble(temperature) * 10); // 更新滑块位置
        temperatureSlider.setValue(temperatureValue);
        maxTokenTextField.setText(String.valueOf(MyPluginSettings.getInstance().getState().maxToken));
        model_nameTextField.setText(String.valueOf(MyPluginSettings.getInstance().getState().model_name));
        api_keyTextField.setText(String.valueOf(MyPluginSettings.getInstance().getState().api_key));
        base_urlTextField.setText(String.valueOf(MyPluginSettings.getInstance().getState().base_url));
    }
    @Override
    public void disposeUIResources() {
        myMainPanel = null;
        modelTypeComboBox = null;
        temperatureTextField = null;
        maxTokenTextField = null;
        model_nameTextField=null;
        api_keyTextField=null;
        base_urlTextField=null;
    }
}
