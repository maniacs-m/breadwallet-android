package com.platform;

import android.util.Log;

import com.breadwallet.presenter.activities.MainActivity;
import com.platform.interfaces.Middleware;
import com.platform.interfaces.Plugin;
import com.platform.middlewares.APIProxy;
import com.platform.middlewares.HTTPFileMiddleware;
import com.platform.middlewares.HTTPIndexMiddleware;
import com.platform.middlewares.HTTPRouter;
import com.platform.middlewares.plugins.CameraPlugin;
import com.platform.middlewares.plugins.GeoLocationPlugin;
import com.platform.middlewares.plugins.KVStorePlugin;
import com.platform.middlewares.plugins.LinkPlugin;
import com.platform.middlewares.plugins.WalletPlugin;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static com.breadwallet.R.string.request;

/**
 * BreadWallet
 * <p/>
 * Created by Mihail Gutan on <mihail@breadwallet.com> 10/17/16.
 * Copyright (c) 2016 breadwallet LLC
 * <p/>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p/>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p/>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
public class HTTPServer {
    public static final String TAG = HTTPServer.class.getName();

    private Set<Middleware> middlewares;
    private Server server;
    public static final int PORT = 31120;
    public static final String URL_EA = "http://localhost:" + PORT + "/ea";

    public HTTPServer() {
        init();
    }

    private void init() {
        middlewares = new LinkedHashSet<>();
        server = new Server(PORT);
        server.setHandler(new ServerHandler());
        setupIntegrations();
    }

    public void startServer() {
        Log.e(TAG, "startServer");
        try {
            if (server != null && !server.isStarted())
                server.start();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void stopServer() {
        Log.e(TAG, "stopServer");
        try {
            if (server != null)
                server.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private class ServerHandler extends AbstractHandler {
        public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
                throws IOException, ServletException {
            boolean success;
            success = dispatch(target, baseRequest, request, response);
            if (!success) {
                Log.e(TAG, "handle: NO MIDDLEWARE HANDLED THE REQUEST: " + target);
            }
        }

    }

    private boolean dispatch(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) {
        boolean result = false;
        if (target.equalsIgnoreCase("/_close")) {
            final MainActivity app = MainActivity.app;
            if (app != null) {
                app.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        app.onBackPressed();
                    }
                });
                response.setStatus(HttpServletResponse.SC_OK);
                return true;
            }

            return false;
        }
        for (Middleware m : middlewares) {
            result = m.handle(target, baseRequest, request, response);
            if (result) {
                Log.e(TAG, "dispatch: " + m.getClass().getName().substring(m.getClass().getName().lastIndexOf(".") + 1) + " succeeded:" + request.getRequestURL());
                break;
            }
        }
        return result;
    }

    private void setupIntegrations() {
        // proxy api for signing and verification
        APIProxy apiProxy = new APIProxy();
        middlewares.add(apiProxy);

        // http router for native functionality
        HTTPRouter httpRouter = new HTTPRouter();
        middlewares.add(httpRouter);

        // basic file server for static assets
        HTTPFileMiddleware httpFileMiddleware = new HTTPFileMiddleware();
        middlewares.add(httpFileMiddleware);

        // middleware to always return index.html for any unknown GET request (facilitates window.history style SPAs)
        HTTPIndexMiddleware httpIndexMiddleware = new HTTPIndexMiddleware();
        middlewares.add(httpIndexMiddleware);

        // geo plugin provides access to onboard geo location functionality
        Plugin geoLocationPlugin = new GeoLocationPlugin();
        httpRouter.appendPlugin(geoLocationPlugin);

        // camera plugin
        Plugin cameraPlugin = new CameraPlugin();
        httpRouter.appendPlugin(cameraPlugin);

        // wallet plugin provides access to the wallet
        Plugin walletPlugin = new WalletPlugin();
        httpRouter.appendPlugin(walletPlugin);

        // link plugin which allows opening links to other apps
        Plugin linkPlugin = new LinkPlugin();
        httpRouter.appendPlugin(linkPlugin);

        // kvstore plugin provides access to the shared replicated kv store
        Plugin kvStorePlugin = new KVStorePlugin();
        httpRouter.appendPlugin(kvStorePlugin);
    }

}
