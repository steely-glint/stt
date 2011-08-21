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

import java.awt.BorderLayout;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import org.xiph.speex.SpeexEncoder;

/**
 *
 * @author tim
 */
class AudioPop extends javax.swing.JFrame implements Runnable {

    private final SpeexEncoder _spxe;
    private TargetDataLine _rec;
    private Thread _reader;
    private int _mean;
    private final Runnable _updater;
    private final JProgressBar _progress;
    GetAudioText _applet;

    public AudioPop(GetAudioText applet) throws LineUnavailableException {
        _applet = applet;
        _progress = new JProgressBar();
        _progress.setValue(0);
        JComponent cp = (JComponent) this.getContentPane();
        cp.setLayout(new BorderLayout());
        JLabel l = new javax.swing.JLabel();
        l.setText("Speak now");
        cp.add(l, BorderLayout.NORTH);
        cp.add(_progress, BorderLayout.CENTER);
        _updater = new Runnable() {

            public void run() {
                _progress.setValue(_mean);
            }
        };
        _spxe = new SpeexEncoder();
        _spxe.init(1, 8, 16000, 1);// _mode, _quality, _sampleRate, _channels);
        _spxe.getEncoder().setComplexity(5);
        _spxe.getEncoder().setVbr(true);
/*
        LineUnavailableException lue = AccessController.doPrivileged(new PrivilegedAction<LineUnavailableException>() {

            public LineUnavailableException run() {
                LineUnavailableException ret = null;
                try {
                    startRec();
                } catch (LineUnavailableException ex) {
                    ret = ex;
                }
                return ret; // nothing to return
            }
        });
        if (lue != null) {
            throw (lue);
        }*/
    }

    private void startRec() throws LineUnavailableException {
        int micSampleRate = 44100;
        int micChannels = 1;
        AudioFormat cdmono = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                micSampleRate, 16, micChannels, 2, 44100.0F, true);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, cdmono);
        _rec = (TargetDataLine) AudioSystem.getLine(info);
        int alen = micSampleRate * 2 * 20 / 1000;
        _rec.open(cdmono, 6 * alen);
        _rec.start();
        _reader = new Thread(this);
        _reader.start();
    }

    void downsample(byte[] in, short[] out) {
        int curr = 0;
        int cnt = 0;
        int val = 0;
        int x = 0;
        int olen = out.length;
        int ilen = in.length;
        int framesz = olen;
        for (int i = 0; i < in.length; i += 2) {
            // walk through the input data
            x = (i * olen) / ilen;
            // see which slot it maps to in the output
            if (x != curr) {
                // if this is a new x, deal with the old one
                val /= cnt; // scale the value by the count that mapped
                // now stick it in the outputbuffer
                out[curr] = (short) val;
                // and reset state, ready to move on
                curr = x;
                cnt = 0;
                val = 0;
            }
            // add this sample to the current total
            val += in[i] << 8;
            val += (0xff & in[i + 1]);
            cnt++; // keep a count of samples added
        }
        // and clean up the end case
        if (cnt != 0) {
            val /= cnt;
            out[curr] = (short) val;
        }

    }

    public void run() {
        int alen = (int) (44100 * 0.02 * 2);
        byte[] abuff = new byte[alen];
        short[] outbuff = new short[320];
        int cnt = 0;
        int silent = 0;
        boolean prevSilent = true;
        boolean done = false;
        ByteArrayOutputStream saveBuff = null;
        try {
            while (!done) {
                int ret = _rec.read(abuff, 0, abuff.length);
                if (alen != ret) {
                    System.err.println("got wrong number of audio bytes" + ret);
                }
                downsample(abuff, outbuff);
                _mean = logmean(outbuff);
                if ((cnt % 10) == 0) {
                    javax.swing.SwingUtilities.invokeLater(_updater);
                }
                if (_mean < 5) {
                    if (prevSilent == true) silent++;
                    prevSilent = true;
                } else {
                    if (saveBuff == null) {
                        saveBuff = new java.io.ByteArrayOutputStream();
                        
                    }
                    silent = 0; // reset the count
                    prevSilent = false;
                }
                if (saveBuff != null) {
                    encodeToBuff(outbuff, saveBuff);
                    if (silent > 25) {
                        done = true;
                    }
                } else {
                    if (silent >100) {
                        done = true;
                    }
                }
                cnt++;

            }
        } catch (IOException x) {
            x.printStackTrace();
        }
        if (saveBuff != null) {
            System.out.println("left with audio data " + saveBuff.size());
        } else {
            System.out.println("left with no audio data ");
        }
        _rec.stop();
        _applet.done(saveBuff);
    }

    private int logmean(short[] outbuff) {
        int mean = 0;
        for (int i = 0; i < outbuff.length; i++) {
            mean += Math.abs(outbuff[i]);
        }
        mean = mean / outbuff.length;
        return (int) Math.max(((10.0 * Math.log(mean)) -50),0) ;
    }

    private void encodeToBuff(short[] outbuff, ByteArrayOutputStream saveBuff) throws IOException {
        byte[] head = new byte[1];
        byte[] enc = new byte[160];
        _spxe.processData(outbuff, 0, outbuff.length);
        int encsize = _spxe.getProcessedData(enc, 0);
        head[0] = (byte) encsize;
        saveBuff.write(head);
        saveBuff.write(enc, 0, encsize);
    }

    void start() {
        try {
            startRec();
        } catch (LineUnavailableException ex) {
            ex.printStackTrace();
        }
    }

}
