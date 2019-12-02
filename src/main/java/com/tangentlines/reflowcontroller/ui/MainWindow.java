package com.tangentlines.reflowcontroller.ui;

import com.tangentlines.reflowcontroller.ApplicationController;

import javax.swing.*;

public class MainWindow extends JFrame {

    public JComboBox txtPort;
    public JButton btnConnect;
    public JButton btnDisconnect;
    public JPanel root;
    public JPanel panelSettings;
    public JSlider slTemperature;
    public JSlider slIntesity;
    public JButton btnSend;
    public JLabel tvPreviewTemperature;
    public JLabel tvPreviewIntensity;
    public JPanel panelStatus;
    public JLabel tvTemperature;
    public JLabel tvTime;
    public JLabel tvTargetTemperature;
    public JLabel tvIntensity;
    public JPanel panelLog;
    public JTextArea txtLog;
    public JLabel tvCommandSince;
    public JLabel tvTempOver;
    public JButton btnRefresh;
    public JButton btnExport;
    public JLabel tvActiveIntensity;
    public JButton btnChart;
    public JButton btnStart;
    public JButton btnStop;
    public JButton btnClear;
    public JComboBox cbProfile;
    public JLabel tvPhase;

    public MainWindow(ApplicationController controller) {

        MainWindowWrapper wrapper = new MainWindowWrapper(this, controller);
        setContentPane(root);

    }

}
