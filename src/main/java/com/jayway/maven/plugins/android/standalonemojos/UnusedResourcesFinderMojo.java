package com.jayway.maven.plugins.android.standalonemojos;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.xml.pull.MXParser;
import org.codehaus.plexus.util.xml.pull.XmlPullParser;

import com.jayway.maven.plugins.android.AbstractAndroidMojo;

/**
 * Checks for unused and unreferenced resources in the resource directory <br/>
 * 
 * @author mattias.rosberg@jayway.com
 * @goal checkResources
 * @requiresProject true
 */
public class UnusedResourcesFinderMojo extends AbstractAndroidMojo {

	private JavaSourceFileResourceExtractor javaSourceFileResourceExtractor;
	private XmlFileResourceExtractor xmlFileResourseExtractor;

	public void execute() throws MojoExecutionException, MojoFailureException {

		// Creating map of different resource types from R.java
		Map<String, Set<String>> resourceMap = new HashMap<String, Set<String>>();
		try {
			// R.java file
			String packageName = extractPackageNameFromAndroidManifest(
					androidManifestFile).trim();
			String[] packageNameParts = packageName.split("\\.");
			StringBuilder genDirBuilder = new StringBuilder();
			genDirBuilder.append(project.getBasedir().getPath()
					+ File.separatorChar + "gen" + File.separatorChar);
			for (int x = 0; x < packageNameParts.length; x++) {
				genDirBuilder.append(packageNameParts[x] + File.separatorChar);
			}
			File RdotJava = new File(genDirBuilder.toString() + "R.java");

			// Read file and extract resource names
			LineNumberReader lnr = new LineNumberReader(
					new FileReader(RdotJava));
			String line;
			String key = null;
			while ((line = lnr.readLine()) != null) {
				if (line.trim().startsWith("public static final class")) {
					int startIndex = line.indexOf("class") + 6;
					int endIndex = line.indexOf("{");
					String resType = line.substring(startIndex, endIndex)
							.trim();
					resourceMap.put(resType, new HashSet<String>());
					key = resType;
				}

				if (line.trim().startsWith("public static final int")) {
					int startIndex = line.indexOf("int") + 4;
					int endIndex = line.indexOf("=");
					String res = line.substring(startIndex, endIndex).trim();
					resourceMap.get(key).add(res);
				}
			}
			lnr.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		// Eliminate used resources from resourceMap
		for (String string : collectReferedResoures()) {
			int endIndex = string.indexOf(".", 2);
			String key = string.substring(2, endIndex);
			String res = string.substring(endIndex + 1, string.length());

			resourceMap.get(key).remove(res);
		}

		resourceMap.remove("id");
		getLog().info(
				"The following resources in R.java are probably not used by your"
						+ " project and the corresponding resource files can be removed.");

		for (String key : resourceMap.keySet()) {
			for (String res : resourceMap.get(key)) {
				getLog().info("R." + key + "." + res);
			}
		}
	}

	public HashSet<String> collectReferedResoures() {
		HashSet<String> allReferedResources = new HashSet<String>();

		// Extract refered resources from Java source files
		List<File> files = retrieveAllFilesWithExtension(sourceDirectory,
				".java");

		for (File file : files) {
			javaSourceFileResourceExtractor = new JavaSourceFileResourceExtractor(
					file);
			allReferedResources.addAll(javaSourceFileResourceExtractor
					.extract());
		}

		// Extract refered resources from XML files in sub directories of /res
		files = retrieveAllFilesWithExtension(resourceDirectory, ".xml");

		for (File file : files) {
			xmlFileResourseExtractor = new XmlFileResourceExtractor(file);
			allReferedResources.addAll(xmlFileResourseExtractor.extract());
		}

		// Extract refered resources from AndroidManifest.xml
		xmlFileResourseExtractor = new XmlFileResourceExtractor(
				androidManifestFile);
		allReferedResources.addAll(xmlFileResourseExtractor.extract());

		return allReferedResources;

	}

	public List<File> retrieveAllFilesWithExtension(File rootDir,
			String fileExtension) {
		List<File> files = new ArrayList<File>();
		// File root = new File(rootDir);
		String[] dirList = rootDir.list();
		for (int i = 0; i < dirList.length; i++) {
			File file = new File(rootDir.getPath() + File.separatorChar
					+ dirList[i]);
			if (file.isDirectory()) {
				files.addAll(retrieveAllFilesWithExtension(
						new File(rootDir.getPath() + File.separatorChar
								+ dirList[i]), fileExtension));
			} else {
				if (dirList[i].endsWith(fileExtension)) {
					files.add(new File(rootDir.getPath() + File.separatorChar
							+ dirList[i]));
				}
			}
		}
		return files;
	}

	private class JavaSourceFileResourceExtractor {

		private File javaSourceFile;

		public JavaSourceFileResourceExtractor(File javaSourceFile) {
			super();
			this.javaSourceFile = javaSourceFile;
		}

		public Set<String> extract() {
			Set<String> referedResources = new HashSet<String>();
			try {
				LineNumberReader lnr = new LineNumberReader(new FileReader(
						javaSourceFile));
				String line;
				while ((line = lnr.readLine()) != null) {
					// ignore out commented lines
					if (line.trim().startsWith("//")) {
						continue;
					} else {
						int endIndex = 0;
						int startIndex = 0;
						while ((startIndex = line.indexOf("R.", endIndex)) != -1) {
							endIndex = Math.min(
									line.indexOf(")", startIndex) > 0 ? line
											.indexOf(")", startIndex) : line
											.length(),
									line.indexOf(",", startIndex) > 0 ? line
											.indexOf(",", startIndex) : line
											.length());
							try {
								referedResources.add(line.substring(startIndex,
										endIndex));
							} catch (Exception e) {
								e.printStackTrace();
								System.out.println("Problem line = " + line);
							}
						}
					}
				}

				lnr.close();

			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return referedResources;
		}

	}

	private class XmlFileResourceExtractor {

		private File xmlFile;

		public XmlFileResourceExtractor(File xmlFile) {
			super();
			this.xmlFile = xmlFile;
		}

		public Set<String> extract() {
			Set<String> referedResources = new HashSet<String>();
			try {
				InputStreamReader reader = new InputStreamReader(
						new FileInputStream(xmlFile));
				XmlPullParser parser = new MXParser();
				parser.setInput(reader);

				while (parser.next() != XmlPullParser.END_DOCUMENT) {
					int count = parser.getAttributeCount();
					for (int a = 0; a < count; a++) {
						extractFromString(referedResources,
								parser.getAttributeValue(a));
					}

					String text = parser.getText();
					extractFromString(referedResources, text);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			return referedResources;
		}

		private void extractFromString(Set<String> referedResources, String text) {
			if (text.startsWith("@drawable")) {
				referedResources.add("R.drawable."
						+ text.replace("@drawable/", ""));
			} else if (text.startsWith("@string")) {
				referedResources
						.add("R.string." + text.replace("@string/", ""));
			} else if (text.startsWith("@color")) {
				referedResources.add("R.color." + text.replace("@color/", ""));
			} else if (text.startsWith("@style")) {
				referedResources.add("R.style." + text.replace("@style/", ""));
			} else if (text.startsWith("@array")) {
				referedResources.add("R.array." + text.replace("@array/", ""));
			}
		}
	}
}
