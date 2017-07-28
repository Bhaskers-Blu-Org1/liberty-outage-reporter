/*******************************************************************************
 * Copyright (c) 2017 IBM Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package wasdev.sample.store;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cloudant.client.api.ClientBuilder;
import com.cloudant.client.api.CloudantClient;
import com.cloudant.client.api.Database;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.gson.JsonObject;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.StringUtils;
import wasdev.sample.Location;

import com.fasterxml.jackson.databind.ObjectMapper;

public class CloudantLocationStore implements LocationStore{

	private Database db = null;
	private static final String databaseName = "mydb";

	public CloudantLocationStore(){
		CloudantClient cloudant = createClient();
		if(cloudant!=null){
		 db = cloudant.database(databaseName, true);
		}
	}

	public Database getDB(){
		return db;
	}

	private static CloudantClient createClient() {

		String url;

		if (System.getenv("VCAP_SERVICES") != null) {
			// When running in Bluemix, the VCAP_SERVICES env var will have the credentials for all bound/connected services
			// Parse the VCAP JSON structure looking for cloudant.
			JsonObject cloudantCredentials = VCAPHelper.getCloudCredentials("cloudant");
			if(cloudantCredentials == null){
				System.out.println("No cloudant database service bound to this application");
				return null;
			}
			url = cloudantCredentials.get("url").getAsString();
		} else {
			System.out.println("Not running with VCAP services. Looking for credentials in cloudant.properties");
			url = VCAPHelper.getLocalProperties("cloudant.properties").getProperty("cloudant_url");
			if(url == null || url.length()==0){
				System.out.println("Explicit URL not set, check for Kubernetes secrets file");
				String kubernetes_secrets_file = VCAPHelper.getLocalProperties("cloudant.properties").getProperty("kubernetes_secrets_file");
				if (kubernetes_secrets_file == null || kubernetes_secrets_file.length() == 0){
					System.out.println("To use a database, set the Cloudant url or kubernetes secret file in src/main/resources/cloudant.properties");
					return null;
				}

				try {
					url = readURLFromKubeSecretsFile(kubernetes_secrets_file);
				} catch (IOException e) {
					System.out.println("Exception when attempting to read kube secrets file: " + e);
					return null;
				}
			}

		}

		try {
			System.out.println("Connecting to Cloudant");
			CloudantClient client = ClientBuilder.url(new URL(url)).build();
			return client;
		} catch (Exception e) {
			System.out.println("Unable to connect to database");
			//e.printStackTrace();
			return null;
		}
	}

	private static String readURLFromKubeSecretsFile(String secretsFile) throws IOException {
		String secretsJson = readKubeSecretsFiles(secretsFile);
		ObjectMapper mapper = new ObjectMapper();
		Map<String, Object> map;

		// convert JSON string to Map
		map = mapper.readValue(secretsJson, new TypeReference<Map<String, String>>(){});
		String url = (String) map.get("url");
		url = url.replaceFirst("https", "http"); //Temporary hack until certificates are figured out.
		System.out.println("url: " + url);
		return url;
	}

	private static String readKubeSecretsFiles(String secretsFile) throws IOException {
		BufferedReader br = new BufferedReader(new FileReader(secretsFile));

		StringBuilder sb = new StringBuilder();
		String line = br.readLine();

		while (line != null) {
			sb.append(line);
			sb.append(System.lineSeparator());
			line = br.readLine();
		}
		String everything = sb.toString();
		br.close();

		return everything;
	}

	@Override
	public Collection<Location> getAll(){
        List<Location> docs;
		try {
			docs = db.getAllDocsRequestBuilder().includeDocs(true).build().getResponse().getDocsAs(Location.class);
		} catch (IOException e) {
			return null;
		}
        return docs;
	}

	@Override
	public Location get(String id) {
		return db.find(Location.class, id);
	}

	@Override
	public Location persist(Location td) {
		String id = db.save(td).getId();
		return db.find(Location.class, id);
	}

	@Override
	public Location update(String id, Location newLocation) {
		Location Location = db.find(Location.class, id);
		Location.setName(newLocation.getName());
		db.update(Location);
		return db.find(Location.class, id);

	}

	@Override
	public void delete(Location location) {
                //Location location = db.find(Location.class, id);
		db.remove(location);
	}

	@Override
	public int count() throws Exception {
		return getAll().size();
	}

}
