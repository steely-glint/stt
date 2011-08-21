// derived in part from work with the following copyright
/*
 * Copyright 2011 Voxeo Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.phonefromhere.stt;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import javax.sound.sampled.LineUnavailableException;
import javax.swing.ImageIcon;
import javax.swing.JApplet;
import javax.swing.JButton;
import netscape.javascript.JSObject;

/**
 *
 * @author tim
 */
public class GetAudioText extends JApplet implements ActionListener {

    /**
     * Initialization method that will be called after the applet is loaded
     * into the browser.
     */
    JButton _mic;
    private AudioPop _popup;
    private JSObject _win;
    private String _callback;

    @Override
    public void init() {
        _mic = new JButton();

        ImageIcon ii = new ImageIcon(this.getClass().getResource("microphone.jpg"));
        _mic.setIcon(ii);
        _mic.addActionListener(this);
        try {
            _popup = new AudioPop(this);
        } catch (LineUnavailableException ex) {
            ex.printStackTrace();
        }
        this.add(_mic);
        _win = JSObject.getWindow(this);
        _callback = getParameter("textCallback");
    }

    public void actionPerformed(ActionEvent ae) {
        _popup.pack();
        _popup.start();
        _popup.show();
    }

    void done(ByteArrayOutputStream saveBuff) {
        _popup.hide();
        String json = "";
        if (saveBuff != null) {
            json = sendSavedBuff(saveBuff);
        } else {
            json = "{\nsilence : true\n}";
        }
        Object [] args = new Object[1];
        args[0] = json;
        if (json.length() >0){
            _win.call(_callback,args);
        }
    }

    private String sendSavedBuff(ByteArrayOutputStream saveBuff) {
        StringBuffer ret = new StringBuffer("");
        try {
            URL u = new URL("https://www.google.com/speech-api/v1/recognize?xjerr=1&client=chromium&lang=en-US");
            URLConnection conn = u.openConnection();

            // No cache may be used, else we might get an old value !!
            conn.setUseCaches(false);
            conn.setRequestProperty("Content-type", "audio/x-speex-with-header-byte; rate=16000");

            // Use the POST method
            conn.setDoInput(true);
            conn.setDoOutput(true);

            // Write the parameters to the URL
            OutputStream outStream;
            outStream = conn.getOutputStream();
            saveBuff.writeTo(outStream);
            outStream.close();
            // Read the answer
            BufferedReader inStream = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;
            if (inStream != null) {

                while (null != (line = inStream.readLine())) {
                    System.out.println("AudioHolder.saveData(): response =" + line);
                    ret.append(line);
                }

            }
        } catch (Exception ex) {
            ret = new StringBuffer("{\nexception : \"" + ex.getMessage() + "\"\n}");
        }
        return ret.toString();
    }
    // TODO overwrite start(), stop() and destroy() methods
}
