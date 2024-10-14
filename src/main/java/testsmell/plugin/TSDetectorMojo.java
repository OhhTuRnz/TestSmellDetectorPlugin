package testsmell.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import java.util.stream.Collectors;

import testsmell.AbstractSmell;
import testsmell.TestFile;
import testsmell.TestSmellDetector;
import thresholds.DefaultThresholds;

@Mojo(name = "detect")
public class TSDetectorMojo extends AbstractMojo {

    // Base directory for running the plug-in
    @Parameter(defaultValue = "${basedir}")
    private String basedir;

    // Test Smell Detector Mojo
    public TSDetectorMojo() {
    }

    public HashMap<String, String> findTestProductionFiles(String dir) throws IOException {
        HashMap<String, String> map = new HashMap<>();

        // Collect test files
        List<String> testFiles = Files.walk(Paths.get(dir))
                .filter(Files::isRegularFile)  // Select only regular files (not directories)
                .filter(path -> path.toString().endsWith("Test.java"))  // Files ending with 'Test.java'
                .map(Path::toString)
                .collect(Collectors.toList());

        // Collect production files
        List<String> prodFiles = Files.walk(Paths.get(dir))
                .filter(Files::isRegularFile)  // Select only regular files (not directories)
                .filter(path -> !path.toString().endsWith("Test.java"))  // Files ending with 'Test.java'
                .map(Path::toString)
                .collect(Collectors.toList());

        for (String testFile : testFiles) {
            String prodFile = testFile;

            // Replace first occurrence of main
            prodFile = prodFile.replaceFirst("test", "main");

            // Suppress ending "Test.java"
            int lastIndex = testFile.lastIndexOf("Test.java");
            if (lastIndex != -1) {
                prodFile = prodFile.substring(0, lastIndex) + ".java";
            }
            if (prodFiles.contains(prodFile))
                map.put(testFile, prodFile);
            else
                map.put(testFile, "");
        }
        return map;
    }

    public void execute() throws MojoExecutionException {
        getLog().info("*** PROJECT BASE DIRECTORY ***");
        getLog().info(basedir);
        getLog().info("*** TEST SMELLS REPORT ***");

        TestSmellDetector testSmellDetector = new TestSmellDetector(new DefaultThresholds());

        String line;

        List<String> columnNames;
        List<String> columnValues;

        columnNames = testSmellDetector.getTestSmellNames();
        columnNames.add(0, "App");
        columnNames.add(1, "TestClass");
        columnNames.add(2, "TestFilePath");
        columnNames.add(3, "ProductionFilePath");
        columnNames.add(4, "RelativeTestFilePath");
        columnNames.add(5, "RelativeProductionFilePath");
        columnNames.add(6, "NumberOfMethods");

        line = String.join(",", columnNames);
        System.out.println(line);

        try {
            // Find JUnit test files
            HashMap<String, String> testFileMap = findTestProductionFiles(basedir);

            TestFile testFile;
            List<TestFile> testFiles = new ArrayList<>();
            for (Map.Entry<String, String> entry : testFileMap.entrySet()) {
                if (entry.getValue() == "")
                    testFile = new TestFile("myApp", entry.getKey(), "");
                else
                    testFile = new TestFile("myApp", entry.getKey(), entry.getValue());
                testFiles.add(testFile);
            }

            /*
               Iterate through all test files to detect smells and then write the output
            */
            TestFile tempFile;
            DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            Date date;

            // Save original System.out
            PrintStream originalOut = System.out;

            // Create a new PrintStream that does nothing
            PrintStream noOpStream = new PrintStream(new OutputStream() {
                @Override
                public void write(int b) {
                    // Do nothing
                }
            });

            for (TestFile file : testFiles) {
                /*
                date = new Date();
                System.out.println(dateFormat.format(date) + " Processing: " + file.getTestFilePath());
                */

                // Redirect System.out to the no-op PrintStream
                System.setOut(noOpStream);
                //detect smells
                tempFile = testSmellDetector.detectSmells(file);
                // Restore original System.out
                System.setOut(originalOut);

                //write output
                columnValues = new ArrayList<>();
                columnValues.add(file.getApp());
                columnValues.add(file.getTestFileName());
                columnValues.add(file.getTestFilePath());
                columnValues.add(file.getProductionFilePath());
                columnValues.add(file.getRelativeTestFilePath());
                columnValues.add(file.getRelativeProductionFilePath());
                columnValues.add(String.valueOf(file.getNumberOfTestMethods()));
                for (AbstractSmell smell : tempFile.getTestSmells()) {
                    try {
                        columnValues.add(String.valueOf(smell.getNumberOfSmellyTests()));
                    } catch (NullPointerException e) {
                        columnValues.add("");
                    }
                }
                line = String.join(",", columnValues);
                System.out.println(line);
            }
        } catch (
                IOException e) {
            e.printStackTrace();
        }
    }

    // For debugging
    public static void main(String[] args) {
        try {
            TSDetectorMojo mojo = new TSDetectorMojo();
            mojo.execute();
        }
        catch (Exception e) {
        }
    }
}