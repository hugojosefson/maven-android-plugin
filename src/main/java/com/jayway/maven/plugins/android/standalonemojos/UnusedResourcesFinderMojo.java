/*
 * Copyright (C) 2011 Jayway AB
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
 * @goal findUnusedResources
 * @requiresProject true
 */
public class UnusedResourcesFinderMojo extends AbstractAndroidMojo {

	private JavaSourceFileResourceExtractor javaSourceFileResourceExtractor;
	private XmlFileResourceExtractor xmlFileResourseExtractor;

	public void execute() throws MojoExecutionException, MojoFailureException {

		// Check that it's not a parent project or instrumentation project
		if (!project.getPackaging().equals("apk")) {
			getLog().info("Not packaging \"apk\" - skipping");
			return;
		}

		String packageName = extractPackageNameFromAndroidManifest(androidManifestFile);
		String targetPackageName = extractTargetPackageNameForInstrumentationTestRunnerFromAndroidManifest(androidManifestFile);
		if (targetPackageName != null && !targetPackageName.equals(packageName)) {
			getLog().info("IntegrationTest project - skipping");
			return;
		}

		// Creating map of different resource types from R.java
		Map<String, Set<String>> resourceMap = new HashMap<String, Set<String>>();
		try {
			File RdotJava = retrieveAllFilesWithExtension(project.getBasedir(),
					"R.java").get(0);

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

		int count = 0;
		StringBuilder output = new StringBuilder();
		for (String key : resourceMap.keySet()) {
			for (String res : resourceMap.get(key)) {
				count++;
				output.append("\nR." + key + "." + res);
			}
		}

		if (count == 0) {
			getLog().info("Found 0 unused resources");
		} else {
			getLog().info("Found " + count + " unused resources");
			getLog().info(
					"The following constants in R.java are not used by your"
							+ " project and the corresponding resource files can be removed:");
			getLog().info(output.toString());
		}
	}

	private String extractTargetPackageNameForInstrumentationTestRunnerFromAndroidManifest(
			File androidManifestFile) {
		try {
			InputStreamReader reader = new InputStreamReader(
					new FileInputStream(androidManifestFile));
			XmlPullParser parser = new MXParser();
			parser.setInput(reader);

			while (parser.next() != XmlPullParser.END_DOCUMENT) {
				switch (parser.getEventType()) {
				case XmlPullParser.START_TAG: {
					if ("instrumentation".equals(parser.getName())) {
						for (int a = 0; a < parser.getAttributeCount(); a++) {
							if (parser.getAttributeName(a).equals(
									"android:targetPackage")) {
								return parser.getAttributeValue(a);
							}
						}

					}
					break;
				}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public HashSet<String> collectReferedResoures() {
		HashSet<String> allReferedResources = new HashSet<String>();

		getLog().info("Looking for unused resources...");

		// Extract refered resources from Java source files
		List<File> files = retrieveAllFilesWithExtension(sourceDirectory,
				".java");

		for (File file : files) {
			javaSourceFileResourceExtractor = new JavaSourceFileResourceExtractor(
					file);
			allReferedResources.addAll(javaSourceFileResourceExtractor
					.extract());
		}

		int nbrFoundInJava = allReferedResources.size();

		// Extract refered resources from XML files in sub directories of /res
		files = retrieveAllFilesWithExtension(resourceDirectory, ".xml");

		for (File file : files) {
			xmlFileResourseExtractor = new XmlFileResourceExtractor(file);
			allReferedResources.addAll(xmlFileResourseExtractor.extract());
		}

		int nbrFoundInJavaAndRes = allReferedResources.size();

		// Extract refered resources from AndroidManifest.xml
		File androidManFile = retrieveAllFilesWithExtension(
				project.getBasedir(), "AndroidManifest.xml").get(0);
		xmlFileResourseExtractor = new XmlFileResourceExtractor(androidManFile);
		allReferedResources.addAll(xmlFileResourseExtractor.extract());

		return allReferedResources;

	}

	public List<File> retrieveAllFilesWithExtension(File rootDir,
			String fileExtension) {
		List<File> files = new ArrayList<File>();
		// File root = new File(rootDir);
		String[] dirList = rootDir.list();
		if (dirList != null) {
			for (int i = 0; i < dirList.length; i++) {
				File file = new File(rootDir.getPath() + File.separatorChar
						+ dirList[i]);
				if (file.isDirectory()) {
					files.addAll(retrieveAllFilesWithExtension(
							new File(rootDir.getPath() + File.separatorChar
									+ dirList[i]), fileExtension));
				} else {
					if (dirList[i].endsWith(fileExtension)) {
						files.add(new File(rootDir.getPath()
								+ File.separatorChar + dirList[i]));
					}
				}
			}
		}
		return files;
	}

	private class JavaSourceFileResourceExtractor {

		private File javaSourceFile;
		private Set<String> validResourceTypes;

		public JavaSourceFileResourceExtractor(File javaSourceFile) {
			super();
			this.javaSourceFile = javaSourceFile;
			validResourceTypes = new HashSet<String>();
			validResourceTypes.add("string");
			validResourceTypes.add("drawable");
			validResourceTypes.add("layout");
			validResourceTypes.add("style");
			validResourceTypes.add("color");
			validResourceTypes.add("array");
			validResourceTypes.add("raw");
			validResourceTypes.add("anim");
			validResourceTypes.add("menu");
			validResourceTypes.add("styleable");
			validResourceTypes.add("xml");
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
								String resourceName = line.substring(
										startIndex, endIndex);
								String[] resourceNameParts = resourceName
										.split("\\.");
								if (validResourceTypes
										.contains(resourceNameParts[1])) {
									referedResources.add(resourceName);
								}
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
			} else if (text.startsWith("@anim")) {
				referedResources.add("R.anim." + text.replace("@anim/", ""));
			} else if (text.startsWith("@styleable")) {
				referedResources.add("R.styleable."
						+ text.replace("@styleable/", ""));
			} else if (text.startsWith("@raw")) {
				referedResources.add("R.raw." + text.replace("@raw/", ""));
			} else if (text.startsWith("@menu")) {
				referedResources.add("R.menu." + text.replace("@menu/", ""));
			} else if (text.startsWith("@xml")) {
				referedResources.add("R.xml." + text.replace("@xml/", ""));
			} else if (text.startsWith("@attr")) {
				referedResources.add("R.attr." + text.replace("@attr/", ""));
			} else if (text.startsWith("@layout")) {
				referedResources
						.add("R.layout." + text.replace("@layout/", ""));
			}
		}
	}
}
