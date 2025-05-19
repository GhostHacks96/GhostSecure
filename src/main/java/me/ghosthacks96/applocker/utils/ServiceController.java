package me.ghosthacks96.applocker.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ServiceController {

    public static boolean isServiceRunning() {
        try {
            // Execute "sc query" command to get the status of the service
            Process process = Runtime.getRuntime().exec("sc query AppBlockerService");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                // Look for the "RUNNING" status in the command output
                if (line.contains("STATE") && line.contains("RUNNING")) {
                    System.out.println("Service is running.");
                    return true;
                }
            }

            process.waitFor(); // Wait for process to complete
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("Service is not running.");
        return false;
    }

    public static void startService() {
        new Thread(() -> {
            try {
                Process process = Runtime.getRuntime().exec("sc start AppBlockerService");
                process.waitFor();
                System.out.println("Service started.");
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public static void stopService() {
        new Thread(() -> {
            try {
                Process process = Runtime.getRuntime().exec("sc stop AppBlockerService");
                process.waitFor();
                System.out.println("Service stopped.");
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public static void installService() {
        new Thread(() -> {
            try {
                Process process = Runtime.getRuntime().exec("AppBlocker.exe install");
                process.waitFor();
                System.out.println("Service installed.");
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }
}