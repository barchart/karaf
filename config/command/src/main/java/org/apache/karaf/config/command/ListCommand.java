/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.karaf.config.command;

import java.util.Dictionary;
import java.util.Enumeration;

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.inject.Service;
import org.osgi.service.cm.Configuration;

@Command(scope = "config", name = "list", description = "Lists existing configurations.")
@Service
public class ListCommand extends ConfigCommandSupport {

    @Argument(index = 0, name = "query", description = "Query in LDAP syntax. Example: \"(service.pid=org.apache.karaf.log)\"", required = false, multiValued = false)
    String query;

    @SuppressWarnings("rawtypes")
    protected Object doExecute() throws Exception {
        Configuration[] configs = configRepository.getConfigAdmin().listConfigurations(query);
        if (configs != null) {
            for (Configuration config : configs) {
                System.out.println("----------------------------------------------------------------");
                System.out.println("Pid:            " + config.getPid());
                if (config.getFactoryPid() != null) {
                    System.out.println("FactoryPid:     " + config.getFactoryPid());
                }
                System.out.println("BundleLocation: " + config.getBundleLocation());
                if (config.getProperties() != null) {
                    System.out.println("Properties:");
                    Dictionary props = config.getProperties();
                    for (Enumeration e = props.keys(); e.hasMoreElements();) {
                        Object key = e.nextElement();
                        System.out.println("   " + key + " = " + props.get(key));
                    }
                }
            }
        }
        return null;
    }
}
