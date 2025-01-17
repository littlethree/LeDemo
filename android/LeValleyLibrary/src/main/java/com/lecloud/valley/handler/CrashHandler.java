package com.lecloud.valley.handler;

import android.content.Context;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.Thread.UncaughtExceptionHandler;

/**
 * 崩溃时写入日志工具类
 *
 * @author raojia
 */
public class CrashHandler implements UncaughtExceptionHandler {
    public static final String TAG = CrashHandler.class.getSimpleName();

    private static CrashHandler instance;
    private Context context;

    private CrashHandler(Context context) {
        this.context = context;
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    public static synchronized CrashHandler getInstance(Context context) {
        if (instance == null) {
            instance = new CrashHandler(context);
        }
        return instance;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        handleException(ex);
    }

    private void handleException(Throwable ex) {
        if (ex == null) {
            return;
        }
        StringBuffer sb = new StringBuffer("程序异常退出 !!!!!!!!!!!!! \n");
        Writer writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);
        ex.printStackTrace(printWriter);
        Throwable cause = ex.getCause();
        while (cause != null) {
            cause.printStackTrace(printWriter);
            cause = cause.getCause();
        }
        String result = writer.toString();
        sb.append(result);
//        LeLog.ePringShenShou(TAG, sb.toString());
        Log.e(TAG, sb.toString());
        new Thread(new Runnable() {

            @Override
            public void run() {
                Looper.prepare();
                Toast.makeText(context, "程序出现异常，请联系RaoJia解决", Toast.LENGTH_SHORT).show();
                Looper.loop();

            }
        }).start();
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.exit(0);
    }

}
