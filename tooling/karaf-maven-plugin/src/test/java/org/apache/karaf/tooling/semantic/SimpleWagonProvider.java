/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.karaf.tooling.semantic;

import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.providers.http.LightweightHttpWagon;
import org.apache.maven.wagon.providers.http.LightweightHttpWagonAuthenticator;
import org.apache.maven.wagon.providers.http.LightweightHttpsWagon;
import org.eclipse.aether.transport.wagon.WagonProvider;

public class SimpleWagonProvider implements WagonProvider {

	@Override
	public Wagon lookup(final String roleHint) throws Exception {

		final LightweightHttpWagonAuthenticator auth = new LightweightHttpWagonAuthenticator();

		if (roleHint.startsWith("file")) {
			return null;
		}

		if (roleHint.startsWith("http")) {
			final LightweightHttpWagon wagon = new LightweightHttpWagon();
			wagon.setAuthenticator(auth);
			return wagon;
		}

		if (roleHint.startsWith("https")) {
			final LightweightHttpsWagon wagon = new LightweightHttpsWagon();
			wagon.setAuthenticator(auth);
			return wagon;
		}

		return null;

	}

	@Override
	public void release(final Wagon wagon) {}

}
