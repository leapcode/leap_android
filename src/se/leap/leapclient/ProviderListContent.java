/**
 * Copyright (c) 2013 LEAP Encryption Access Project and contributers
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
 package se.leap.leapclient;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.net.URL;
import java.net.MalformedURLException;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Models the provider list shown in the ConfigurationWizard.
 * 
 * @author parmegv
 *
 */
public class ProviderListContent {

	public static List<ProviderItem> ITEMS = new ArrayList<ProviderItem>();

	public static Map<String, ProviderItem> ITEM_MAP = new HashMap<String, ProviderItem>();

	/**
	 * Adds a new provider item to the end of the items map, and to the items list.
	 * @param item
	 */
	public static void addItem(ProviderItem item) {
		ITEMS.add(item);
		ITEM_MAP.put(String.valueOf(ITEMS.size()), item);
	}
	public static void removeItem(ProviderItem item) {
		ITEMS.remove(item);
		ITEM_MAP.remove(item);
	}

	/**
	 * A provider item.
	 */
	public static class ProviderItem {
		final public static String CUSTOM = "custom";
		final public static String DANGER_ON = "danger_on";
		private boolean custom = false;
		private String provider_json_url;
		private String name;
		private boolean danger_on = false;

		/**
		 * @param name of the provider
		 * @param urls_file_input_stream file input stream linking with the assets url file
		 * @param custom if it's a new provider entered by the user or not
		 * @param danger_on if the user trusts completely the new provider
		 */
		public ProviderItem(String name, InputStream urls_file_input_stream, boolean custom, boolean danger_on) {

			try {
				byte[] urls_file_bytes = new byte[urls_file_input_stream.available()];
				urls_file_input_stream.read(urls_file_bytes);
				String urls_file_content = new String(urls_file_bytes);
				JSONObject file_contents = new JSONObject(urls_file_content);
				provider_json_url = file_contents.getString(Provider.DOT_JSON_URL);
				this.name = name;
				this.custom = custom;
				this.danger_on = danger_on;
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		/**
		 * @param name of the provider
		 * @param provider_json_url used to download provider.json file of the provider
		 * @param provider_json already downloaded
		 * @param custom if it's a new provider entered by the user or not
		 * @param danger_on if the user trusts completely the new provider
		 */
		public ProviderItem(String name, String provider_json_url, boolean custom, boolean danger_on) {
			this.name = name;
			this.provider_json_url = provider_json_url;
			this.custom = custom;
			this.danger_on = danger_on;
		}
		
		public String name() { return name; }
		
		public boolean custom() { return custom; }
		
		public String providerJsonUrl() { return provider_json_url; }
		
		public boolean completelyTrusted() { return danger_on; }
	}
}
