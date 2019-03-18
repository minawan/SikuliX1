/*
 * Copyright (c) 2010-2018, sikuli.org, sikulix.com - MIT license
 */

package org.sikuli.ide;

import org.sikuli.basics.Debug;
import org.sikuli.util.ProcessRunner;

import javax.swing.*;
import java.io.File;
import java.io.FilenameFilter;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import io.humble.video.Codec;
import io.humble.video.Decoder;
import io.humble.video.Demuxer;
import io.humble.video.DemuxerStream;
import io.humble.video.Global;
import io.humble.video.Media;
import io.humble.video.MediaDescriptor;
import io.humble.video.MediaPacket;
import io.humble.video.MediaPicture;
import io.humble.video.Rational;
import io.humble.video.awt.ImageFrame;
import io.humble.video.awt.MediaPictureConverter;
import io.humble.video.awt.MediaPictureConverterFactory;
import io.humble.video.customio.FfmpegIO;
import io.humble.video.customio.FfmpegIOHandle;
import io.humble.video.customio.HumbleIO;
import io.humble.video.customio.IURLProtocolHandler;
//import io.humble.video.customio.ReadableWritableChannelHandler;
import io.humble.video.customio.InputOutputStreamHandler;
import io.humble.video.customio.URLProtocolManager;

class DeviceInfo {
    public String deviceName;
    public int width, height;
    DeviceInfo(String deviceName, int width, int height) {
      this.deviceName = deviceName;
      this.width = width;
      this.height = height;
    }
}

public class Sikulix {

  static boolean verbose = true;
  static String jarName = "";
  static File sxFolder = null;
  static File fAppData;
  static File fDirExtensions = null;
  static File[] fExtensions = null;
  static List<String> extensions = new ArrayList<>();
  static String ClassPath = "";
  static String start = String.format("%d", new Date().getTime());
  static String osName = System.getProperty("os.name").substring(0, 1).toLowerCase();
  static String jythonVersion = "2.7.1";
  static String jrubyVersion = "9.2.0.0";
  static boolean moveJython = false;
  static boolean moveJRuby = false;
  static boolean jythonLatest = false;
  static boolean jrubyLatest = false;
  private static String[] commands = {
    "adb push /home/sushi/SikuliX1/scrcpy/x/server/scrcpy-server.jar /data/local/tmp/scrcpy-server.jar",
    "adb reverse localabstract:scrcpy tcp:27183",
    "adb shell CLASSPATH=/data/local/tmp/scrcpy-server.jar app_process / com.genymobile.scrcpy.Server 0 8000000 false"
  };

  private static int parseSize(byte[] buf, int offset) {
    byte[] value = new byte[4];
    System.arraycopy(buf, offset, value, 2, 2);
    return ByteBuffer.wrap(value).getInt();
  }

  private static final int DEVICE_NAME_FIELD_LENGTH = 64;
  private static DeviceInfo readDeviceInfo(Socket deviceSocket)
      throws IOException {
    String deviceName;
    int num_bytes_to_read = DEVICE_NAME_FIELD_LENGTH + 4;
    Integer width, height;
    byte[] buf = new byte[num_bytes_to_read];
    BufferedInputStream istream = new BufferedInputStream(
                                        deviceSocket.getInputStream());
    int received_bytes = istream.read(buf, 0, num_bytes_to_read);
    if (received_bytes < num_bytes_to_read) {
      log(1, "Could not retrieve device information");
      return null;
    }
    buf[DEVICE_NAME_FIELD_LENGTH - 1] = 0;
    int nameLength;
    for (nameLength = 0; buf[nameLength] != 0; ++nameLength) {
      // Find the index of null terminating character.
    }
    deviceName = new String(buf, 0, nameLength);
    width = parseSize(buf, DEVICE_NAME_FIELD_LENGTH);
    height = parseSize(buf, DEVICE_NAME_FIELD_LENGTH + 2);
    return new DeviceInfo(deviceName, width, height);
  }

  public static void main(String[] args) {
    log(1, "Entering main...");
    log(1, "Getting runtime...");
    for (String cmd : commands) {
      try {
        Runtime runtime = Runtime.getRuntime();
        log(1, "Running process...");
        Process process = runtime.exec(cmd);
        new Thread(new Runnable() {
          public void run() {
            BufferedReader input =
              new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = null; 

            try {
              log(1, "Before while...");
              while ((line = input.readLine()) != null)
                log(1, line);
              log(1, "After while...");
            } catch (IOException e) {
              e.printStackTrace();
            }
          }
        }).start();
        //process.waitFor();
      } catch (IOException e) {
        log(1, "IOException");
        e.printStackTrace();
      /*} catch (InterruptedException e) {
        log(1, "InterruptedException");
        e.printStackTrace();*/
      }
    }
    int portNumber = 27183;
    log(1, "Port: %d", portNumber);
    try {
      ServerSocket serverSocket = new ServerSocket(portNumber);
      Socket clientSocket = serverSocket.accept();
      PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
      InputStream stream = clientSocket.getInputStream();
      BufferedReader in = new BufferedReader(new InputStreamReader(stream));
      DeviceInfo deviceInfo = readDeviceInfo(clientSocket);
      log(1, "Device Name: %s", deviceInfo.deviceName);
      log(1, "Screen Dimensions: %dx%d", deviceInfo.width, deviceInfo.height);
      HumbleIO mFactory = HumbleIO.getFactory();
      //if (channel == null) {
      //  throw new IOException("hey yo client socket channel is null");
      //}
      //mFactory.mapIO(protocol, new ReadableWritableChannelHandler(channel, null, true), true);
      String protocol = "myprotocol";
      mFactory.mapIO(protocol + ":yeah", new InputOutputStreamHandler(stream, null, true), true);
      URLProtocolManager.getManager().registerFactory(protocol, mFactory);
      //SocketChannel channel = clientSocket.getChannel();
      long retval = 0;
      // Call url_open wrapper
      FfmpegIOHandle handle = new FfmpegIOHandle();

      retval = FfmpegIO.url_open(handle, protocol + ":yeah",
          IURLProtocolHandler.URL_RDONLY_MODE);
      if (retval < 0) {
        log(1, "url_open(myprotocol:yeah) failed: %ld", retval);
        return;
      }
      // call url_read wrapper
      byte[] buffer = new byte[1024];
      while ((retval = FfmpegIO.url_read(handle, buffer, buffer.length)) > 0)
      {
        log(1, String.format("bytesRead == %d", retval));
      }

      // call url_close wrapper
      retval = FfmpegIO.url_close(handle);
      if (retval < 0) {
        log(1, "url_close failed: %ld", retval);
        return;
      }
      // 1. Initialize muxer.
      // 1.1 Register either customio.InputOutputStreamHandler or
      //     customio.ReadableWritableChannelHandler to URLProtocolManager.
      // 2. Initialize codec.
      //Codec codec = Codec.findDecodingCodec(Codec.ID.CODEC_ID_H264);
      // 3.Initialize decoder.
      //decoder.getCodecType() == MediaDescriptor.Type.MEDIA_VIDEO
    } catch (IOException e) {
      log(1, "IOException");
      e.printStackTrace();
    } catch (Exception e) {
      log(1, "Exception");
      e.printStackTrace();
    }
  /*
    CodeSource codeSrc = SikuliIDE.class.getProtectionDomain().getCodeSource();
    if (codeSrc != null && codeSrc.getLocation() != null) {
      try {
        jarName = codeSrc.getLocation().getPath();
        jarName = URLDecoder.decode(jarName, "utf8");
      } catch (UnsupportedEncodingException e) {
        log(-1, "URLDecoder: not possible: %s", jarName);
        System.exit(1);
      }
      sxFolder = new File(jarName).getParentFile();
    }

    if (args.length > 0 && args[0].startsWith("-v")) {
      verbose = true;
      args[0] += start;
      Debug.globalTraceOn();
    }

    fAppData = makeAppData();
    log(1, "Running: %s", jarName);
    log(1, "AppData: %s", fAppData);

    if (jarName.endsWith(".jar")) {
      log(1, "starting");
      ClassPath = jarName;
    } else {
      SikulixRunIDE.main(args);
      return;
    }
    File[] sxFolderList = sxFolder.listFiles(new FilenameFilter() {
      @Override
      public boolean accept(File dir, String name) {
        if (name.endsWith(".jar")) {
          if (name.contains("jython") && name.contains("standalone")) {
            moveJython = true;
            return true;
          }
          if (name.contains("jruby") && name.contains("complete")) {
            moveJRuby = true;
            return true;
          }
        }
        return false;
      }
    });

    fDirExtensions = new File(fAppData, "Extensions");
    boolean extensionsOK = true;

    if (!fDirExtensions.exists()) {
      fDirExtensions.mkdir();
    }

    if (!fDirExtensions.exists()) {
      log(1, "folder extension not available: %s", fDirExtensions);
      extensionsOK = false;
    }

    if (extensionsOK) {
      fExtensions = fDirExtensions.listFiles();
      if (moveJython || moveJRuby) {
        for (File fExtension : fExtensions) {
          String name = fExtension.getName();
          if ((name.contains("jython") && name.contains("standalone")) ||
                  (name.contains("jruby") && name.contains("complete"))) {
            fExtension.delete();
          }
        }
      }
      if (sxFolderList.length > 0) {
        for (File fJar : sxFolderList) {
          try {
            Files.move(fJar.toPath(), fDirExtensions.toPath().resolve(fJar.toPath().getFileName()), StandardCopyOption.REPLACE_EXISTING);
            log(1, "moving to extensions: %s", fJar);
          } catch (IOException e) {
            log(-1, "moving to extensions: %s (%s)", fJar, e.getMessage());
          }
        }
      }

      log(1, "looking for extension jars in: %s", fDirExtensions);
      String separator = File.pathSeparator;
      fExtensions = fDirExtensions.listFiles();
      for (File fExtension : fExtensions) {
        if (!ClassPath.isEmpty()) {
          ClassPath += separator;
        }
        String pExtension = fExtension.getAbsolutePath();
        if (pExtension.endsWith(".jar")) {
          if (pExtension.contains("jython") && pExtension.contains("standalone")) {
            if (pExtension.contains(jythonVersion)) {
              jythonLatest = true;
            }
          } else if (pExtension.contains("jruby") && pExtension.contains("complete")) {
            if (pExtension.contains(jrubyVersion)) {
              jrubyLatest = true;
            }
          }
          ClassPath += pExtension;
          extensions.add(pExtension);
          log(1, "adding extension: %s", fExtension);
        }
      }
    }
    if (!jythonLatest && !jrubyLatest) {
      log(-1, "Neither Jython nor JRuby available - IDE not yet useable with JavaScript only" +
              "\nPlease consult the docs for a solution");
      System.exit(-1);
    }

    List<String> cmd = new ArrayList<>();
    cmd.add("java");
//    if ("m".equals(osName)) {
//      cmd.add("-Xdock:name=SikuliX");
//      cmd.add("-Xdock:icon=\"" + new File(fAppData, "SikulixLibs/sikulix.icns").getAbsolutePath() + "\"");
//    }
    cmd.add("-cp");
    cmd.add(ClassPath);
    cmd.add("org.sikuli.ide.SikulixRunIDE");
    cmd.addAll(Arrays.asList(args));
    int exitValue = ProcessRunner.detach(cmd);
    log(1, "terminating: returned: %d", exitValue);
    */
  }

  private static void log(int level, String msg, Object... args) {
    String timestamp = Calendar.getInstance().getTime().toString();
    String msgShow = timestamp + " [DEBUG] RunIDE: " + msg;
    if (level < 0) {
      msgShow = timestamp + "[ERROR] RunIDE: " + msg;
    } else if (!verbose) {
      return;
    }
    //System.out.println(String.format(msgShow, args));
    System.out.println(msgShow);
  }

  private static File makeAppData() {
    File fSikulixAppPath = new File("");
    File fAppPath = new File("");
    File fUserDir = null;
    String userHome = System.getProperty("user.home");
    if (userHome == null || userHome.isEmpty() || !(fUserDir = new File(userHome)).exists()) {
      log(-1, "JavaSystemProperty::user.home not valid: %s", userHome);
      System.exit(-1);
    } else {
      if ("w".equals(osName)) {
        String appPath = System.getenv("APPDATA");
        if (appPath != null && !appPath.isEmpty()) {
          fAppPath = new File(appPath);
          fSikulixAppPath = new File(fAppPath, "Sikulix");
        }
      } else if ("m".equals(osName)) {
        fAppPath = new File(fUserDir, "Library/Application Support");
        fSikulixAppPath = new File(fAppPath, "Sikulix");
      } else {
        fAppPath = fUserDir;
        fSikulixAppPath = new File(fAppPath, ".Sikulix");
      }
      if (!fSikulixAppPath.exists()) {
        fSikulixAppPath.mkdirs();
      }
      if (!fSikulixAppPath.exists()) {
        log(-1, "JavaSystemProperty::user.home not valid: %s", userHome);
        System.exit(-1);
      }
    }
    return fSikulixAppPath;
  }

  protected static void prepareMac() {
    try {
      // set the brushed metal look and feel, if desired
      System.setProperty("apple.awt.brushMetalLook", "true");

      // use the mac system menu bar
      System.setProperty("apple.laf.useScreenMenuBar", "true");

      // set the "About" menu item name
      System.setProperty("com.apple.mrj.application.apple.menu.about.name", "WikiStar");

      // use smoother fonts
      System.setProperty("apple.awt.textantialiasing", "true");

      // ref: http://developer.apple.com/releasenotes/Java/Java142RNTiger/1_NewFeatures/chapter_2_section_3.html
      System.setProperty("apple.awt.graphics.EnableQ2DX", "true");

      // use the system look and feel
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    } catch (Exception e) {
      // put your debug code here ...
    }
  }
}
