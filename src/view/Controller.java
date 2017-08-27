package view;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.FileWriter;
import java.io.IOException;

import java.net.URL;
import java.util.*;


import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;



import Controller.DBconn;

import model.Complex;

import model.FFT;

import org.tritonus.sampled.convert.PCM2PCMConversionProvider;
import org.tritonus.share.sampled.convert.*;

/**
 * Created by rocks on 6/29/2017.
 */



public class Controller implements Initializable {
    public String filePath=null;
    boolean running = false;
    double highscores[][];
    double recordPoints[][];
    long points[][];
    HashMap<Integer, Integer> counthashMap = new HashMap<>();
    ArrayList<Long> hashMatch = new ArrayList<>();

    @FXML
    private TextField songTitle;

    @FXML
    private TextField showName;

    @FXML
    private Label storeSongLabel;

    @FXML
    private Label find_message_label;

    @FXML
    public ProgressIndicator progressIndicator;

    @FXML
    public ProgressIndicator progressIndicatorStoreSound;

    @FXML
    public TextArea matchtextAreaLabel;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        progressIndicator.setVisible(false);
        progressIndicatorStoreSound.setVisible(false);
    }

    DBconn dBconn = new DBconn();

    long nrSong = 0;

    private AudioFormat getFormat() {
        float sampleRate = 44100;
        int sampleSizeInBits = 8;
        int channels = 1; // mono
        boolean signed = true;
        boolean bigEndian = true;
        return new AudioFormat(sampleRate, sampleSizeInBits, channels, signed,
                bigEndian);
    }

    private synchronized SourceDataLine getLine(AudioFormat audioFormat)
            throws LineUnavailableException {
        SourceDataLine res = null;
        DataLine.Info info = new DataLine.Info(SourceDataLine.class,
                audioFormat);
        res = (SourceDataLine) AudioSystem.getLine(info);
        res.open(audioFormat);
        return res;
    }



    private synchronized void rawplay(AudioFormat targetFormat,
                                      AudioInputStream din) throws IOException, LineUnavailableException {
        byte[] data = new byte[4096];
        SourceDataLine line = getLine(targetFormat);
        if (line != null) {
            // Start
            line.start();
            int nBytesRead = 0, nBytesWritten = 0;
            while (nBytesRead != -1) {
                nBytesRead = din.read(data, 0, data.length);
                if (nBytesRead != -1) {
                    nBytesWritten = line.write(data, 0, nBytesRead);
                }

            }
            // Stop
            line.drain();
            line.stop();
            line.close();
            din.close();
        }

    }

    private void listenSound(long songId, boolean isMatching, String title, String show)
            throws LineUnavailableException, IOException, Exception,
            UnsupportedAudioFileException {
        if (!isMatching) {
            dBconn.insertMusic(title, show);
        }
        AudioFormat formatTmp = null;
        TargetDataLine lineTmp = null;
        String filePath = this.filePath;
        AudioInputStream din = null;
        AudioInputStream outDin = null;
        PCM2PCMConversionProvider conversionProvider = new PCM2PCMConversionProvider();
        boolean isMicrophone = false;

        if (filePath == null || filePath.equals("")) {

            formatTmp = getFormat(); // Fill AudioFormat with the wanted
            // settings
            DataLine.Info info = new DataLine.Info(TargetDataLine.class,
                    formatTmp);
            lineTmp = (TargetDataLine) AudioSystem.getLine(info);
            isMicrophone = true;
            System.out.println("using microphone");
        } else {
            AudioInputStream in;


            File file = new File(filePath);
            filePath=null;
            in = AudioSystem.getAudioInputStream(file);


            AudioFormat baseFormat = in.getFormat();

            //System.out.println(baseFormat.toString());

            AudioFormat decodedFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.getSampleRate(), 16, baseFormat.getChannels(),
                    baseFormat.getChannels() * 2, baseFormat.getSampleRate(),
                    false);

            din = AudioSystem.getAudioInputStream(decodedFormat, in);

            if (!conversionProvider.isConversionSupported(getFormat(),
                    decodedFormat)) {

                System.out.println("Conversion is not supported");
                final String res="The audio file format is not supported";
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        find_message_label.setText(res);
                    }
                });
            }

            //System.out.println(decodedFormat.toString());

            outDin = conversionProvider.getAudioInputStream(getFormat(), din);
            formatTmp = decodedFormat;

            DataLine.Info info = new DataLine.Info(TargetDataLine.class,
                    formatTmp);
            lineTmp = (TargetDataLine) AudioSystem.getLine(info);
        }

        final AudioFormat format = formatTmp;
        final TargetDataLine line = lineTmp;
        final boolean isMicro = isMicrophone;
        final AudioInputStream outDinSound = outDin;

        if (isMicro) {
            try {
                line.open(format);
                line.start();
                isMatching=true;
            } catch (LineUnavailableException e) {
                e.printStackTrace();
            }
        }

        final long sId = songId;
        final boolean isMatch = isMatching;

        Thread listeningThread = new Thread(new Runnable() {
            public void run() {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                running = true;
                int n = 0;
                byte[] buffer = new byte[(int) 1024];

                try {
                    while (running) {
                        n++;
                        if (n > 1000)
                            break;

                        int count = 0;
                        if (isMicro) {
                            count = line.read(buffer, 0, 1024);
                        } else {
                            count = outDinSound.read(buffer, 0, 1024);
                        }
                        if (count > 0) {
                            out.write(buffer, 0, count);
                        }
                    }

                    byte b[] = out.toByteArray();

                    System.out.println(b.length);
//                    for (int i = 0; i < b.length; i++) {
//                        System.out.print("\t" + b[i]);
//                    }

                    try {
                        makeSpectrum(out, sId, isMatch);

                        FileWriter fstream = new FileWriter("out.txt");
                        BufferedWriter outFile = new BufferedWriter(fstream);

                        byte bytes[] = out.toByteArray();
                        for (int i = 0; i < b.length; i++) {
                            outFile.write("" + b[i] + ";");
                        }
                        outFile.close();

                    } catch (Exception e) {
                        System.err.println("Error: " + e.getMessage());
                    }

                    out.close();
                    line.close();
                } catch (IOException e) {
                    System.err.println("I/O problems: " + e);
                    System.exit(-1);
                }

            }

        });

        listeningThread.start();
    }

    void makeSpectrum(ByteArrayOutputStream out, long songId, boolean isMatching) {
        byte audio[] = out.toByteArray();
        System.out.println(audio.length);
        final int totalSize = audio.length;


        int amountPossible = totalSize / 4096;

        // When turning into frequency domain we'll need complex numbers:
        Complex[][] results = new Complex[amountPossible][];

        // For all the chunks:
        for (int times = 0; times < amountPossible; times++) {
            Complex[] complex = new Complex[4096];
            for (int i = 0; i < 4096; i++) {
                // Put the time domain data into a complex number with imaginary
                // part as 0:
                complex[i] = new Complex(audio[(times * 4096) + i], 0);
                //	System.out.println(audio[(times * 4096) + i]);
               // System.out.println(complex[i].re()+ " "+complex[i].im());
            }
           // System.out.println("complex");

            // Perform FFT analysis on the chunk:
            results[times] = FFT.fft(complex);

           // System.out.println("Transformend FFT values "+results[times]);
        }
        System.out.println("Sound id" + songId + "isMatiching" + isMatching);
        determineKeyPoints(results, songId, isMatching);

    }

    public final int LOWER_LIMIT = 40;
    public final int UPPER_LIMIT = 300;
    public final int[] RANGE = new int[]{40, 80, 120, 180, UPPER_LIMIT + 1};

    // Find out in which range
    public int getIndex(int freq) {
        int i = 0;
        while (RANGE[i] < freq)
            i++;
        return i;
    }

    void determineKeyPoints(Complex[][] results, long songId, boolean isMatching) {

        FileWriter fstream = null;
        try {
            fstream = new FileWriter("result.txt");
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        BufferedWriter outFile = new BufferedWriter(fstream);

        highscores = new double[results.length][5];
        for (int i = 0; i < results.length; i++) {
            for (int j = 0; j < 5; j++) {
                highscores[i][j] = 0;
            }
        }

        recordPoints = new double[results.length][UPPER_LIMIT];
        for (int i = 0; i < results.length; i++) {
            for (int j = 0; j < UPPER_LIMIT; j++) {
                recordPoints[i][j] = 0;
            }
        }

        points = new long[results.length][5];
        for (int i = 0; i < results.length; i++) {
            for (int j = 0; j < 5; j++) {
                points[i][j] = 0;
            }
        }

        for (int t = 0; t < results.length; t++) {
            for (int freq = LOWER_LIMIT; freq < UPPER_LIMIT - 1; freq++) {
                // Get the magnitude:
                double mag = Math.log(results[t][freq].abs() + 1);

                // Find out which range we are in:
                int index = getIndex(freq);

                // Save the highest magnitude and corresponding frequency:
                if (mag > highscores[t][index]) {
                    highscores[t][index] = mag;
                    recordPoints[t][freq] = 1;
                    points[t][index] = freq;
                }
            }

            try {
                for (int k = 0; k < 5; k++) {
                    outFile.write("" + highscores[t][k] + ";"
                            + recordPoints[t][k] + "\t");
                }
                outFile.write("\n");

            } catch (IOException e) {
                e.printStackTrace();
            }
//            System.out.println("The frequency value of peaks "+points[t][0]+
//            "\t"+points[t][1]+"\t"+points[t][2]+"\t"+points[t][3]);

            long h = hash(points[t][0], points[t][1], points[t][2],
                    points[t][3]);

           // System.out.println("generated hash from those point "+h);
            if (!isMatching) {
                try {
                    dBconn.insertHash(h, t);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }


            if (isMatching) {
                this.hashMatch.add(h);
            }


        }
        if(!isMatching)
        {
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    storeSongLabel.setText("Music information and hashes added successfully");
                    progressIndicatorStoreSound.setVisible(true);
                    progressIndicatorStoreSound.setProgress(100);

                }
            });
        }


        ArrayList<Integer> sondID = new ArrayList<>();
        if (isMatching) {
            try {
                ArrayList<DBconn.Record> records = dBconn.returnAllHash();

                int previd = records.get(0).getId();
                int prevtime = records.get(0).getTime();
                long prevhash = records.get(0).getHash();
                int nextid;
                int nexttime;
                long nexthash;
                //System.out.println(records.size() + "col count");

                for (int i = 1; i < records.size(); i++) {

                    nextid = records.get(i).getId();
                    nexttime = records.get(i).getTime();
                    nexthash = records.get(i).getHash();
                    for (int j = 1; j < hashMatch.size(); j++) {
                        //System.out.println("prev hash"+prevhash);
                        //System.out.println("prev id"+previd);
                        if ((hashMatch.get(j - 1) == prevhash) && (hashMatch.get(j) == nexthash)) {
                            if ((nexttime - prevtime) == 1) {
                                //System.out.println("match");
                                if (counthashMap.containsKey(previd)) {
                                    int count = counthashMap.get(previd) + 1;

                                    counthashMap.put(previd, count);
                                } else {
                                    counthashMap.put(previd, 1);

                                    sondID.add(previd);
                                }
                            }
                        }
                    }
                    previd = nextid;
                    prevtime = nexttime;
                    prevhash = nexthash;
                }

                System.out.println(counthashMap);
                int max = 0;
                int maxid = 0;
                for (int i = 0; i < counthashMap.size(); i++) {
                    if (counthashMap.get(sondID.get(i)) > max) {
                        max = counthashMap.get(sondID.get(i));
                        maxid = sondID.get(i);
                    }
                }
                ShowResults(counthashMap, dBconn.returnSongInfo(),maxid,sondID);
                counthashMap.clear();


            } catch (Exception e) {
                e.printStackTrace();
            }

        }
        filePath=null;
        try {
            outFile.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private static final int FUZ_FACTOR = 2;

    private long hash(long p1, long p2, long p3, long p4) {
        return (p4 - (p4 % FUZ_FACTOR)) * 100000000 + (p3 - (p3 % FUZ_FACTOR))
                * 100000 + (p2 - (p2 % FUZ_FACTOR)) * 100
                + (p1 - (p1 % FUZ_FACTOR));
    }


    public void ShowResults(HashMap<Integer,Integer> countResult,HashMap<Integer,String> musicInfo,Integer highestId,ArrayList<Integer> songID)
    {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                progressIndicator.setVisible(true);
                progressIndicator.setProgress(100);
            }
        });
        String textAreaMessage="";
        String result="";
        for (int i = 0; i < songID.size() ; i++) {
            textAreaMessage=textAreaMessage+"Music "+musicInfo.get(songID.get(i)).split(":")[0]+" of show "+musicInfo.get(songID.get(i)).split(":")[1]+" has "+countResult.get(songID.get(i))+" matches.\n";
        }
        matchtextAreaLabel.setText(textAreaMessage);
        System.out.println(countResult.get(highestId)+" ala");
        try {
            if(counthashMap.size()!=1) {
                if (musicInfo.get(highestId) != null && countResult.get(highestId) >= 4) {
                    result = result + "\nThe predicted show is " + musicInfo.get(highestId).split(":")[1];
                } else {
                    result = "No sufficient matching hashes found.\nThe show cannot be predicted";
                }
            }
            else {

                if (musicInfo.get(highestId) != null && countResult.get(highestId) >= 2) {
                    result = result + "\nThe predicted show is " + musicInfo.get(highestId).split(":")[1];
                } else {
                    result = "No sufficient matching hashes found.\nThe show cannot be predicted";
                }
            }
        }catch (IndexOutOfBoundsException e)
        {
            result="No sufficient matching hashes found\nThe show cannot be predicted";
        }
        final String res=result;
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                find_message_label.setText(res);
            }
        });

        System.out.println(result);
        counthashMap.clear();
        hashMatch.clear();
    }



    public void addSongButtonPress(ActionEvent e) throws Exception
    {
//        System.out.println(songTitle.getText());
//        System.out.println(showName.getText());
//        System.out.println(filePath);
        String info="";
        if(songTitle.getText()==null||showName.getText()==null||filePath==null) {
            storeSongLabel.applyCss();
            info="The provided details are not complete please try again";
            final String infoFinal=info;
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    progressIndicatorStoreSound.setVisible(true);
                    progressIndicatorStoreSound.setProgress(100);
                    storeSongLabel.setText(infoFinal);
                }
            });

        }
        else {

            info="Creating and storing the fingerprints";
            final String infoFinale=info;
            Platform.runLater(new Runnable() {
                @Override
                public void run() {

                    progressIndicatorStoreSound.setVisible(true);
                    progressIndicatorStoreSound.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
                    storeSongLabel.setText(infoFinale);
                }
            });
            try {
                listenSound(nrSong, false, songTitle.getText(), showName.getText());
            }
            catch (Exception exp)
            {
                info="The audio file is not supported";
                final String infoFinal=info;
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        progressIndicatorStoreSound.setVisible(true);
                        progressIndicatorStoreSound.setProgress(100);
                        storeSongLabel.setText(infoFinal);
                    }
                });

            }
        }



    }
    public void chooseFileButtonPress(ActionEvent e) throws Exception
    {
        FileChooser fileChooser=new FileChooser();
        File selectedFile=fileChooser.showOpenDialog(null);
        if(selectedFile!=null)
        {
            String path=selectedFile.getAbsolutePath();
            filePath=path;
        }
    }
    public void uploadFileStartMatch() throws Exception
    {
        try {
            listenSound(nrSong, true, "", "");
            String info="";
            info="Searching for the matching fingerprints in the database";
            final String infoFinal=info;
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    progressIndicator.setVisible(true);
                    progressIndicator.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
                    find_message_label.setText(infoFinal);
                }
            });
        }catch (Exception e)
        {
            String info="";
            info="The file format is not supported";
            final String infoFinal=info;
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    progressIndicatorStoreSound.setVisible(true);
                    progressIndicatorStoreSound.setProgress(100);
                    find_message_label.setText(infoFinal);
                }
            });

        }
    }

    public void startMatch() throws Exception
    {
        try {
            listenSound(nrSong, true, "", "");
            final String res="Searching for the matching fingerprints in the database";
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    progressIndicator.setVisible(true);
                    progressIndicator.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
                    find_message_label.setText(res);
                }
            });
        }
        catch (Exception e)
        {
            String info="";
            info="The audio file is not supported";
            final String infoFinal=info;
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    progressIndicatorStoreSound.setVisible(true);
                    progressIndicatorStoreSound.setProgress(100);
                    storeSongLabel.setText(infoFinal);
                }
            });

        }
    }
    public void stopMatch()
    {
        running = false;
    }



}