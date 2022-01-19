/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2015 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package org.glassfish.jersey.examples.helloworld;

import com.wavefront.internal.reporter.SdkReporter;
import com.wavefront.opentracing.WavefrontTracer;
import com.wavefront.opentracing.reporting.CompositeReporter;
import com.wavefront.opentracing.reporting.ConsoleReporter;
import com.wavefront.opentracing.reporting.Reporter;
import com.wavefront.opentracing.reporting.WavefrontSpanReporter;
import com.wavefront.sdk.common.WavefrontSender;
import com.wavefront.sdk.common.application.ApplicationTags;
import com.wavefront.sdk.common.clients.WavefrontClient;
import com.wavefront.sdk.jersey.WavefrontJerseyFilter;
import com.wavefront.sdk.jersey.reporter.WavefrontJerseyReporter;

import java.io.IOException;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.jersey.filter.LoggingFilter;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.server.ServerProperties;

import io.opentracing.Tracer;

/**
 * Hello world!
 */
public class App {

    private static final URI BASE_URI = URI.create("http://localhost:8080/base/");
    public static final String ROOT_PATH = "helloworld";
    private static WavefrontJerseyFilter wavefrontJerseyFilter;
    private static Logger logger = Logger.getLogger(App.class.getCanonicalName());

    public static void main(String[] args) {
        try {
            System.out.println("\"Hello World\" Jersey Example App");


            ApplicationTags applicationTags =
                new ApplicationTags.Builder("go-jersey", "hello-jersey").build();
            WavefrontSender wavefrontSender =
                new WavefrontClient.Builder("http://localhost")
                    .metricsPort(2878)
                    .tracesPort(30001)
                    .build();

            String SOURCE = "goppegard-a01";

            SdkReporter wfJerseyReporter =
                new WavefrontJerseyReporter.Builder(applicationTags).withSource(SOURCE).build(wavefrontSender);
            ConsoleReporter consoleReporter = new ConsoleReporter(SOURCE);


            Reporter spanRecorder =
                new WavefrontSpanReporter.Builder().withSource(SOURCE).build(wavefrontSender);
            Reporter reporter = new CompositeReporter(consoleReporter, spanRecorder);
            Tracer tracer = new WavefrontTracer.Builder(reporter, applicationTags).withGlobalTag(
                "debug", "true").withGlobalTag("error", "true").build();
            WavefrontJerseyFilter.Builder filterBuilder =
                new WavefrontJerseyFilter.Builder(wfJerseyReporter,
                applicationTags).withTracer(tracer);
            wavefrontJerseyFilter = filterBuilder.build();

            final MyApplication resourceConfig = new MyApplication();
            final HttpServer server = GrizzlyHttpServerFactory.createHttpServer(BASE_URI, resourceConfig, false);
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run() {
                    server.shutdownNow();
                }
            }));
            server.start();

            System.out.println(String.format("Application started.\nTry out %s%s\nStop the application using CTRL+C",
                    BASE_URI, ROOT_PATH));
            Thread.currentThread().join();
        } catch (IOException | InterruptedException ex) {
            Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public static class MyApplication extends ResourceConfig {
        public MyApplication() {
            register(new LoggingFilter(logger, true));
            register(wavefrontJerseyFilter);
            property(ServerProperties.TRACING, "ALL");
            register(HelloWorldResource.class);
        }
    }
}
