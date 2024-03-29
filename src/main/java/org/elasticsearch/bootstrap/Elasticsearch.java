/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.bootstrap;

import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import joptsimple.OptionSpecBuilder;
import joptsimple.util.PathConverter;
import org.elasticsearch.Build;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.cli.ExitCodes;
import org.elasticsearch.cli.EnvironmentAwareCommand;
import org.elasticsearch.cli.Terminal;
import org.elasticsearch.cli.UserException;
import org.elasticsearch.client.Requests;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.query.QueryStringQueryBuilder;
import org.elasticsearch.monitor.jvm.JvmInfo;
import org.elasticsearch.node.NodeValidationException;
import org.elasticsearch.transport.client.PreBuiltTransportClient;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.security.Permission;
import java.util.Arrays;
import java.util.Map;

/**
 * This class starts elasticsearch.
 */
class Elasticsearch extends EnvironmentAwareCommand {

    private final OptionSpecBuilder versionOption;
    private final OptionSpecBuilder daemonizeOption;
    private final OptionSpec<Path> pidfileOption;
    private final OptionSpecBuilder quietOption;

    // visible for testing
    Elasticsearch() {
        super("starts elasticsearch");
        versionOption = parser.acceptsAll(Arrays.asList("V", "version"),
            "Prints elasticsearch version information and exits");
        daemonizeOption = parser.acceptsAll(Arrays.asList("d", "daemonize"),
            "Starts Elasticsearch in the background")
            .availableUnless(versionOption);
        pidfileOption = parser.acceptsAll(Arrays.asList("p", "pidfile"),
            "Creates a pid file in the specified path on start")
            .availableUnless(versionOption)
            .withRequiredArg()
            .withValuesConvertedBy(new PathConverter());
        quietOption = parser.acceptsAll(Arrays.asList("q", "quiet"),
            "Turns off standard ouput/error streams logging in console")
            .availableUnless(versionOption)
            .availableUnless(daemonizeOption);
    }

    /**
     * Main entry point for starting elasticsearch
     */
    public static void main(final String[] args) throws Exception {
        // we want the JVM to think there is a security manager installed so that if internal policy decisions that would be based on the
        // presence of a security manager or lack thereof act as if there is a security manager present (e.g., DNS cache policy)
        System.setSecurityManager(new SecurityManager() {
            @Override
            public void checkPermission(Permission perm) {
                // grant all permissions so that we can later set the security manager to the one that we want
            }
        });
        final Elasticsearch elasticsearch = new Elasticsearch();
        int status = main(args, elasticsearch, Terminal.DEFAULT);
        if (status != ExitCodes.OK) {
            exit(status);
        }
        
        
       

        
    }

    static int main(final String[] args, final Elasticsearch elasticsearch, final Terminal terminal) throws Exception {
        return elasticsearch.main(args, terminal);
    }

    @Override
    protected void execute(Terminal terminal, OptionSet options, Environment env) throws UserException {
        if (options.nonOptionArguments().isEmpty() == false) {
            throw new UserException(ExitCodes.USAGE, "Positional arguments not allowed, found " + options.nonOptionArguments());
        }
        if (options.has(versionOption)) {
            if (options.has(daemonizeOption) || options.has(pidfileOption)) {
                throw new UserException(ExitCodes.USAGE, "Elasticsearch version option is mutually exclusive with any other option");
            }
            terminal.println("Version: " + org.elasticsearch.Version.CURRENT
                    + ", Build: " + Build.CURRENT.shortHash() + "/" + Build.CURRENT.date()
                    + ", JVM: " + JvmInfo.jvmInfo().version());
            return;
        }

        final boolean daemonize = options.has(daemonizeOption);
        final Path pidFile = pidfileOption.value(options);
        final boolean quiet = options.has(quietOption);

        try {
            init(daemonize, pidFile, quiet, env);
        } catch (NodeValidationException e) {
            throw new UserException(ExitCodes.CONFIG, e.getMessage());
        }
    }

    void init(final boolean daemonize, final Path pidFile, final boolean quiet, Environment initialEnv)
        throws NodeValidationException, UserException {
        try {
            Bootstrap.init(!daemonize, pidFile, quiet, initialEnv);
        } catch (BootstrapException | RuntimeException e) {
            // format exceptions to the console in a special way
            // to avoid 2MB stacktraces from guice, etc.
            throw new StartupException(e);
        }
    }

    /**
     * Required method that's called by Apache Commons procrun when
     * running as a service on Windows, when the service is stopped.
     *
     * http://commons.apache.org/proper/commons-daemon/procrun.html
     *
     * NOTE: If this method is renamed and/or moved, make sure to
     * update elasticsearch-service.bat!
     */
    static void close(String[] args) throws IOException {
        Bootstrap.stop();
    }
    


}
