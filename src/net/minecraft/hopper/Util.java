package net.minecraft.hopper;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.Charset;

public class Util {
	public static String performPost(final URL url, final String parameters, final Proxy proxy, final String contentType, final boolean returnErrorPage) throws IOException {
		final HttpURLConnection connection = (HttpURLConnection) url.openConnection(proxy);
		final byte[] paramAsBytes = parameters.getBytes(Charset.forName("UTF-8"));
		connection.setConnectTimeout(15000);
		connection.setReadTimeout(15000);
		connection.setRequestMethod("POST");
		connection.setRequestProperty("Content-Type", contentType + "; charset=utf-8");
		connection.setRequestProperty("Content-Length", "" + paramAsBytes.length);
		connection.setRequestProperty("Content-Language", "en-US");
		connection.setUseCaches(false);
		connection.setDoInput(true);
		connection.setDoOutput(true);
		final DataOutputStream writer = new DataOutputStream(connection.getOutputStream());
		writer.write(paramAsBytes);
		writer.flush();
		writer.close();
		BufferedReader reader;
		try {
			reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		} catch (IOException e) {
			if (!returnErrorPage) {
				throw e;
			}
			final InputStream stream = connection.getErrorStream();
			if (stream == null) {
				throw e;
			}
			reader = new BufferedReader(new InputStreamReader(stream));
		}
		final StringBuilder response = new StringBuilder();
		String line;
		while ((line = reader.readLine()) != null) {
			response.append(line);
			response.append('\r');
		}
		reader.close();
		return response.toString();
	}
	
	public static URL constantURL(final String input) {
		try {
			return new URL(input);
		} catch (MalformedURLException e) {
			throw new Error(e);
		}
	}
}
