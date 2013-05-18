/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package common;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author Andrew
 */
public class Logger {

    public static class Instance {

        private final String who;

        public Instance(String who) {
            this.who = who;
        }

        public void log(Object obj) {
            if (obj != null) {
                Logger.log(who, obj.toString());
            }
        }
    }
    
    private static boolean started = false;
    private static final List<String> queue = new LinkedList<>();
    private static final List<String> tempQueue = new LinkedList<>();
    private static final DateFormat df = new SimpleDateFormat("[yyyy-MM-dd HH:mm:ss.SSS] ");
    private static final Thread thread = new Thread() {
        @Override
        public void run() {
            try {
                while (!this.isInterrupted()) {
                    synchronized (queue) {
                        if (!queue.isEmpty()) {
                            tempQueue.addAll(queue);
                            queue.clear();
                        }
                    }

                    if (!tempQueue.isEmpty()) {
                        for (String m : tempQueue) {
                            System.out.println(m);
                            FileIO.append(m, "log.txt");
                        }
                        tempQueue.clear();
                    } else {
                        Thread.sleep(10);
                    }
                }
            } catch (Exception ex) {
            }
        }
    };

    private static void log(String who, String message) {
        if (!started) {
            thread.start();
            started = true;
        }

        synchronized (queue) {
            queue.add(df.format(Calendar.getInstance().getTime()) + who + " | " + message);
        }
    }
}
