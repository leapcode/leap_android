/**
 * Copyright (c) 2019 LEAP Encryption Access Project and contributers
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package se.leap.bitmaskclient.pluggableTransports;

import android.content.Context;
import android.support.annotation.WorkerThread;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.StringTokenizer;


/**
 * Created by cyberta on 22.02.19.
 */

public class Dispatcher {
    private static final String ASSET_KEY = "piedispatcher";
    private static final String TAG = Dispatcher.class.getName();
    private final String remoteIP;
    private final String remotePort;
    private final String certificate;
    private final String iatMode;
    private File fileDispatcher;
    private Context context;
    private String port = "";
    private Thread dispatcherThread = null;
    private int dipatcherPid = -1;

    public Dispatcher(Context context, String remoteIP, String remotePort, String certificate, String iatMode) {
        this.context = context.getApplicationContext();
        this.remoteIP = remoteIP;
        this.remotePort = remotePort;
        this.certificate = certificate;
        this.iatMode = iatMode;
    }

    @WorkerThread
    public void initSync() {
        try {
            fileDispatcher = installDispatcher();

            // start dispatcher
            dispatcherThread = new Thread(() -> {
                try {
                    StringBuilder dispatcherLog = new StringBuilder();
                    String dispatcherCommand = fileDispatcher.getCanonicalPath() +
                            " -transparent" +
                            " -client" +
                            " -state " + context.getFilesDir().getCanonicalPath() + "/state" +
                            " -target " + remoteIP + ":" + remotePort +
                            " -transports obfs4" +
                            " -options \"" + String.format("{\\\"cert\\\": \\\"%s\\\", \\\"iatMode\\\": \\\"%s\\\"}\"", certificate, iatMode) +
                            " -logLevel DEBUG -enableLogging";

                    runBlockingCmd(new String[]{dispatcherCommand}, dispatcherLog);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            dispatcherThread.start();

            // get pid of dispatcher
            StringBuilder log = new StringBuilder();
            String pidCommand = "ps | grep " + fileDispatcher.getCanonicalPath();
            runBlockingCmd(new String[]{pidCommand}, log);
            String output  = log.toString();
            StringTokenizer st = new StringTokenizer(output, " ");
            st.nextToken(); // proc owner
            dipatcherPid = Integer.parseInt(st.nextToken().trim());

            // get open port of dispatcher
            String getPortCommand = "cat " + context.getFilesDir().getCanonicalPath() + "/state/dispatcher.log | grep \"obfs4 - registered listener\"";
            long timeout = System.currentTimeMillis() + 5000;
            int i = 1;
            while (this.port.length() == 0 && System.currentTimeMillis() < timeout) {
                log = new StringBuilder();
                Log.d(TAG, i + ". try to get port");
                runBlockingCmd(new String[]{getPortCommand}, log);
                output = log.toString();
                if (output.length() > 0) {
                    Log.d(TAG, "dispatcher log: \n =================\n" + output);
                }

                String dispatcherLog[] = output.split(" ");
                if (dispatcherLog.length > 0) {
                    String localAddressAndPort = dispatcherLog[dispatcherLog.length - 1];
                    if (localAddressAndPort.contains(":")) {
                        this.port = localAddressAndPort.split(":")[1].replace(System.getProperty("line.separator"), "");
                        Log.d(TAG, "local port is: " + this.port);
                    }
                }
                i += 1;
            }

        } catch(Exception e){
            if (dispatcherThread.isAlive()) {
                Log.e(TAG, e.getMessage() + ". Shutting down Dispatcher thread.");
                stop();
            }
        }
    }

    public String getPort() {
        return port;
    }

    public void stop() {
        Log.d(TAG, "Shutting down Dispatcher thread.");
        if (dispatcherThread != null && dispatcherThread.isAlive()) {
            try {
                killProcess(dipatcherPid);
            } catch (Exception e) {
                e.printStackTrace();
            }
            dispatcherThread.interrupt();
        }
    }

    private void killProcess(int pid) throws Exception {
        String killPid = "kill -9 " + pid;
        runCmd(new String[]{killPid}, null, false);
    }

    public boolean isRunning() {
        return dispatcherThread != null && dispatcherThread.isAlive();
    }

    private File installDispatcher(){
        File fileDispatcher = null;
        BinaryInstaller bi = new BinaryInstaller(context,context.getFilesDir());

        String arch = System.getProperty("os.arch");
        if (arch.contains("arm"))
            arch = "arm";
        else
            arch = "x86";

        try {
            fileDispatcher = bi.installResource(arch, ASSET_KEY, false);
        } catch (Exception ioe) {
            Log.d(TAG,"Couldn't install dispatcher: " + ioe);
        }

        return fileDispatcher;
    }

    @WorkerThread
    private void runBlockingCmd(String[] cmds, StringBuilder log) throws Exception {
        runCmd(cmds, log, true);
    }

    @WorkerThread
    private int runCmd(String[] cmds, StringBuilder log,
                       boolean waitFor) throws Exception {

        int exitCode = -1;
        Process proc = Runtime.getRuntime().exec("sh");
        OutputStreamWriter out = new OutputStreamWriter(proc.getOutputStream());

        try {
            for (String cmd : cmds) {
                Log.d(TAG, "executing CMD: " + cmd);
                out.write(cmd);
                out.write("\n");
            }

            out.flush();
            out.write("exit\n");
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            out.close();
        }

        if (waitFor) {
            // Consume the "stdout"
            InputStreamReader reader = new InputStreamReader(proc.getInputStream());
            readToLogString(reader, log);

            // Consume the "stderr"
            reader = new InputStreamReader(proc.getErrorStream());
            readToLogString(reader, log);

            try {
                exitCode = proc.waitFor();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        return exitCode;
    }

    private void readToLogString(InputStreamReader reader, StringBuilder log) throws IOException {
        final char buf[] = new char[10];
        int read = 0;
        try {
            while ((read = reader.read(buf)) != -1) {
                if (log != null)
                    log.append(buf, 0, read);
            }
        } catch (IOException e) {
            reader.close();
            throw new IOException(e);
        }
        reader.close();
    }
}
