package hudson.plugins.octopusdeploy.cli;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Result;
import hudson.model.TaskListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class LegacyCliWrapperTest {

    @Mock
    private FilePath workspace;

    @Mock
    private Launcher launcher;

    @Mock
    private EnvVars environment;

    @Mock
    private TaskListener listener;

    @Mock
    private PrintStream logger;

    private TestableLegacyCliWrapper cliWrapper;

    @BeforeEach
    public void setUp() throws IOException, InterruptedException {
        when(listener.getLogger()).thenReturn(logger);

        cliWrapper = spy(new TestableLegacyCliWrapper(
                "test-tool-id",
                workspace,
                launcher,
                environment,
                listener,
                "https://octopus.example.com",
                "API-KEY123",
                "Spaces-1",
                "TestProject",
                true,
                false
        ));

        // Mock the execute method to return success
        doReturn(new CliExecutionResult("", 0))
                .when(cliWrapper)
                .execute(anyList(), anySet());
    }

    @Test
    public void pack_callsExecuteWithCorrectArguments() throws IOException, InterruptedException {
        // Arrange
        String packageId = "MyPackage";
        String packageVersion = "1.0.0";
        String format = "zip";
        String sourcePath = "/source";
        List<String> includePaths = Arrays.asList("file1.txt", "file2.txt");
        String outputPath = "/output";
        boolean overwriteExisting = true;
        String additionalArgs = "--verbose";

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> argsCaptor = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<Integer>> maskedCaptor = ArgumentCaptor.forClass(Set.class);

        // Act
        Result result = cliWrapper.pack(packageId, packageVersion, format, sourcePath,
                includePaths, outputPath, overwriteExisting, additionalArgs);

        // Assert
        assertThat(result).isEqualTo(Result.SUCCESS);

        // Verify execute was called once (no separate login in legacy CLI)
        verify(cliWrapper, times(1)).execute(argsCaptor.capture(), maskedCaptor.capture());

        List<String> packArgs = argsCaptor.getValue();

        // Verify pack command structure (legacy format)
        assertThat(packArgs).startsWith("pack");
        assertThat(packArgs).contains("--id", packageId);
        assertThat(packArgs).contains("--version", packageVersion);
        assertThat(packArgs).contains("--format", format);
        assertThat(packArgs).contains("--basePath", sourcePath);
        assertThat(packArgs).contains("--include", "file1.txt");
        assertThat(packArgs).contains("--include", "file2.txt");
        assertThat(packArgs).contains("--outFolder", outputPath);
        assertThat(packArgs).contains("--overwrite");
        assertThat(packArgs).contains("--verbose");
        assertThat(packArgs).contains("--server", "https://octopus.example.com");
        assertThat(packArgs).contains("--apiKey", "API-KEY123");
        assertThat(packArgs).contains("--space", "Spaces-1");
        assertThat(packArgs).contains("--debug");
    }

    @Test
    public void push_callsExecuteWithCorrectArguments() throws IOException, InterruptedException {
        // Arrange
        List<String> packagePaths = Arrays.asList("/path/to/package1.zip", "/path/to/package2.zip");
        String overwriteMode = "OverwriteExisting";
        String additionalArgs = "--timeout 300";

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> argsCaptor = ArgumentCaptor.forClass(List.class);

        // Act
        Result result = cliWrapper.push(packagePaths, overwriteMode, additionalArgs);

        // Assert
        assertThat(result).isEqualTo(Result.SUCCESS);

        // Verify execute was called once
        verify(cliWrapper, times(1)).execute(argsCaptor.capture(), anySet());

        List<String> pushArgs = argsCaptor.getValue();

        // Verify push command structure
        assertThat(pushArgs).startsWith("push");
        assertThat(pushArgs).contains("--package", "/path/to/package1.zip");
        assertThat(pushArgs).contains("--package", "/path/to/package2.zip");
        assertThat(pushArgs).contains("--overwrite-mode", overwriteMode);
        assertThat(pushArgs).contains("--timeout");
        assertThat(pushArgs).contains("300");
        assertThat(pushArgs).contains("--server", "https://octopus.example.com");
        assertThat(pushArgs).contains("--apiKey", "API-KEY123");
        assertThat(pushArgs).contains("--space", "Spaces-1");
        assertThat(pushArgs).contains("--debug");
    }

    @Test
    public void pushBuildInformation_callsExecuteWithCorrectArguments() throws IOException, InterruptedException {
        // Arrange
        List<String> packageIds = Arrays.asList("Package1", "Package2");
        String version = "1.0.0";
        String filePath = "/path/to/buildinfo.json";
        String overwriteMode = "FailIfExists";
        String additionalArgs = "--debug";

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> argsCaptor = ArgumentCaptor.forClass(List.class);

        // Act
        Result result = cliWrapper.pushBuildInformation(packageIds, version, filePath,
                overwriteMode, additionalArgs);

        // Assert
        assertThat(result).isEqualTo(Result.SUCCESS);

        // Verify execute was called once
        verify(cliWrapper, times(1)).execute(argsCaptor.capture(), anySet());

        List<String> buildInfoArgs = argsCaptor.getValue();

        // Verify build-information command structure
        assertThat(buildInfoArgs).startsWith("build-information");
        assertThat(buildInfoArgs).contains("--package-id", "Package1");
        assertThat(buildInfoArgs).contains("--package-id", "Package2");
        assertThat(buildInfoArgs).contains("--version", version);
        assertThat(buildInfoArgs).contains("--file", filePath);
        assertThat(buildInfoArgs).contains("--overwrite-mode", overwriteMode);
        assertThat(buildInfoArgs).contains("--debug");
        assertThat(buildInfoArgs).contains("--server", "https://octopus.example.com");
        assertThat(buildInfoArgs).contains("--apiKey", "API-KEY123");
        assertThat(buildInfoArgs).contains("--space", "Spaces-1");
    }

    @Test
    public void deployRelease_callsExecuteWithCorrectArguments() throws IOException, InterruptedException {
        // Arrange
        String version = "1.0.0";
        String environment = "Production";
        String tenant = "TenantA";
        String tenantTag = "Region/US";
        List<String> variables = Arrays.asList("var1=value1", "var2=value2");
        boolean waitForDeployment = false;
        String deploymentTimeout = null;
        boolean cancelOnTimeout = false;
        String additionalArgs = "--force";

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> argsCaptor = ArgumentCaptor.forClass(List.class);

        // Act
        Result result = cliWrapper.deployRelease(version, environment, tenant, tenantTag,
                variables, waitForDeployment, deploymentTimeout, cancelOnTimeout, additionalArgs);

        // Assert
        assertThat(result).isEqualTo(Result.SUCCESS);

        // Verify execute was called once
        verify(cliWrapper, times(1)).execute(argsCaptor.capture(), anySet());

        List<String> deployArgs = argsCaptor.getValue();

        // Verify deploy command structure (legacy format uses deploy-release and --deployTo)
        assertThat(deployArgs).startsWith("deploy-release");
        assertThat(deployArgs).contains("--project", "TestProject");
        assertThat(deployArgs).contains("--version", version);
        assertThat(deployArgs).contains("--deployTo", environment);
        assertThat(deployArgs).contains("--tenant", tenant);
        assertThat(deployArgs).contains("--tenantTag", tenantTag);
        assertThat(deployArgs).contains("--variable", "var1=value1");
        assertThat(deployArgs).contains("--variable", "var2=value2");
        assertThat(deployArgs).contains("--force");
        assertThat(deployArgs).contains("--server", "https://octopus.example.com");
        assertThat(deployArgs).contains("--apiKey", "API-KEY123");
        assertThat(deployArgs).contains("--space", "Spaces-1");
        assertThat(deployArgs).contains("--debug");
    }

    @Test
    public void createRelease_callsExecuteWithCorrectArguments() throws IOException, InterruptedException {
        // Arrange
        String version = "1.0.0";
        String channel = "Default";
        String releaseNotes = "Release notes here";
        String defaultPackageVersion = "1.0.0";
        List<String> packages = Arrays.asList("Package1:1.0.1", "Package2:1.0.2");
        String gitRef = "refs/heads/main";
        String gitCommit = "abc123";
        String deployToEnvironment = null; // Not deploying immediately
        String tenant = null;
        String tenantTag = null;
        List<String> variables = Collections.emptyList();
        boolean waitForDeployment = false;
        String deploymentTimeout = null;
        boolean cancelOnTimeout = false;
        String additionalArgs = null;

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> argsCaptor = ArgumentCaptor.forClass(List.class);

        // Act
        Result result = cliWrapper.createRelease(version, channel, releaseNotes,
                defaultPackageVersion, packages, gitRef, gitCommit,
                deployToEnvironment, tenant, tenantTag, variables,
                waitForDeployment, deploymentTimeout, cancelOnTimeout, additionalArgs);

        // Assert
        assertThat(result).isEqualTo(Result.SUCCESS);

        // Verify execute was called once
        verify(cliWrapper, times(1)).execute(argsCaptor.capture(), anySet());

        List<String> createArgs = argsCaptor.getValue();

        // Verify create release command structure (legacy format uses create-release)
        assertThat(createArgs).startsWith("create-release");
        assertThat(createArgs).contains("--project", "TestProject");
        assertThat(createArgs).contains("--version", version);
        assertThat(createArgs).contains("--channel", channel);
        assertThat(createArgs).contains("--releaseNotes", releaseNotes);
        assertThat(createArgs).contains("--packageVersion", defaultPackageVersion);
        assertThat(createArgs).contains("--package", "Package1:1.0.1");
        assertThat(createArgs).contains("--package", "Package2:1.0.2");
        assertThat(createArgs).contains("--gitRef", gitRef);
        assertThat(createArgs).contains("--gitCommit", gitCommit);
        assertThat(createArgs).contains("--server", "https://octopus.example.com");
        assertThat(createArgs).contains("--apiKey", "API-KEY123");
        assertThat(createArgs).contains("--space", "Spaces-1");
        assertThat(createArgs).contains("--debug");
    }

    @Test
    public void pack_withMinimalArguments_callsExecuteWithCorrectArguments() throws IOException, InterruptedException {
        // Arrange
        String packageId = "MinimalPackage";
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> argsCaptor = ArgumentCaptor.forClass(List.class);

        // Act
        Result result = cliWrapper.pack(packageId, null, null, null, null, null, false, null);

        // Assert
        assertThat(result).isEqualTo(Result.SUCCESS);
        verify(cliWrapper, times(1)).execute(argsCaptor.capture(), anySet());

        List<String> packArgs = argsCaptor.getValue();

        // Verify minimal pack command structure
        assertThat(packArgs).startsWith("pack");
        assertThat(packArgs).contains("--id", packageId);
        assertThat(packArgs).doesNotContain("--version");
        assertThat(packArgs).doesNotContain("--format");
        assertThat(packArgs).doesNotContain("--basePath");
        assertThat(packArgs).doesNotContain("--overwrite");
    }

    @Test
    public void push_withNullOverwriteMode_doesNotIncludeOverwriteFlag() throws IOException, InterruptedException {
        // Arrange
        List<String> packagePaths = Collections.singletonList("/path/to/package.zip");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> argsCaptor = ArgumentCaptor.forClass(List.class);

        // Act
        Result result = cliWrapper.push(packagePaths, null, null);

        // Assert
        assertThat(result).isEqualTo(Result.SUCCESS);
        verify(cliWrapper, times(1)).execute(argsCaptor.capture(), anySet());

        List<String> pushArgs = argsCaptor.getValue();

        // Verify overwrite mode is NOT included when null
        assertThat(pushArgs).doesNotContain("--overwrite-mode");
        assertThat(pushArgs).contains("--package", "/path/to/package.zip");
    }

    @Test
    public void execute_isMaskedForApiKey() throws IOException, InterruptedException {
        // Arrange
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> argsCaptor = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<Integer>> maskedCaptor = ArgumentCaptor.forClass(Set.class);

        // Act
        cliWrapper.pack("TestPackage", null, null, null, null, null, false, null);

        // Assert
        verify(cliWrapper, times(1)).execute(argsCaptor.capture(), maskedCaptor.capture());

        List<String> allArgs = argsCaptor.getValue();
        Set<Integer> maskedIndices = maskedCaptor.getValue();

        // The API key position should be masked
        assertThat(maskedIndices).isNotEmpty();
        Integer[] loginMaskedArray = maskedIndices.toArray(new Integer[0]);
        assertThat(allArgs.get(loginMaskedArray[0] - 1)).isEqualTo("API-KEY123");
    }

    @Test
    public void deployRelease_withWaitForDeployment_includesWaitFlags() throws IOException, InterruptedException {
        // Arrange
        String version = "1.0.0";
        String environment = "Production";
        String tenant = "TenantA";
        String tenantTag = "Region/US";
        List<String> variables = Arrays.asList("var1=value1");
        boolean waitForDeployment = true;
        String deploymentTimeout = "00:15:30"; // Legacy CLI passes this as-is
        boolean cancelOnTimeout = true;
        String additionalArgs = null;

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> argsCaptor = ArgumentCaptor.forClass(List.class);

        // Act
        Result result = cliWrapper.deployRelease(version, environment, tenant, tenantTag,
                variables, waitForDeployment, deploymentTimeout, cancelOnTimeout, additionalArgs);

        // Assert
        assertThat(result).isEqualTo(Result.SUCCESS);

        // Verify execute was called once (legacy CLI handles wait inline)
        verify(cliWrapper, times(1)).execute(argsCaptor.capture(), anySet());

        List<String> deployArgs = argsCaptor.getValue();

        // Verify deploy command includes wait flags
        assertThat(deployArgs).startsWith("deploy-release");
        assertThat(deployArgs).contains("--project", "TestProject");
        assertThat(deployArgs).contains("--version", version);
        assertThat(deployArgs).contains("--deployTo", environment);
        assertThat(deployArgs).contains("--progress");
        assertThat(deployArgs).contains("--deploymentTimeout", deploymentTimeout);
        assertThat(deployArgs).contains("--cancelOnTimeout");
    }

    @Test
    public void createRelease_withDeployToEnvironment_includesDeploymentInSameCommand() throws IOException, InterruptedException {
        // Arrange
        String version = "1.0.0";
        String channel = "Default";
        String releaseNotes = "Release notes";
        String defaultPackageVersion = "1.0.0";
        List<String> packages = Collections.singletonList("Package1:1.0.1");
        String gitRef = "refs/heads/main";
        String gitCommit = "abc123";
        String deployToEnvironment = "Development"; // Deploy after creating release
        String tenant = "TenantB";
        String tenantTag = "Region/EU";
        List<String> variables = Arrays.asList("var1=value1", "var2=value2");
        boolean waitForDeployment = true;
        String deploymentTimeout = "00:10:00";
        boolean cancelOnTimeout = false;
        String additionalArgs = "--debug";

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> argsCaptor = ArgumentCaptor.forClass(List.class);

        // Act
        Result result = cliWrapper.createRelease(version, channel, releaseNotes,
                defaultPackageVersion, packages, gitRef, gitCommit,
                deployToEnvironment, tenant, tenantTag, variables,
                waitForDeployment, deploymentTimeout, cancelOnTimeout, additionalArgs);

        // Assert
        assertThat(result).isEqualTo(Result.SUCCESS);

        // Verify execute was called once (legacy CLI handles create+deploy in one command)
        verify(cliWrapper, times(1)).execute(argsCaptor.capture(), anySet());

        List<String> createArgs = argsCaptor.getValue();

        // Verify create release command includes all release creation AND deployment parameters
        assertThat(createArgs).startsWith("create-release");
        assertThat(createArgs).contains("--project", "TestProject");
        assertThat(createArgs).contains("--version", version);
        assertThat(createArgs).contains("--channel", channel);
        assertThat(createArgs).contains("--releaseNotes", releaseNotes);
        assertThat(createArgs).contains("--packageVersion", defaultPackageVersion);
        assertThat(createArgs).contains("--package", "Package1:1.0.1");
        assertThat(createArgs).contains("--gitRef", gitRef);
        assertThat(createArgs).contains("--gitCommit", gitCommit);
        // Deployment parameters in the same command
        assertThat(createArgs).contains("--deployTo", deployToEnvironment);
        assertThat(createArgs).contains("--tenant", tenant);
        assertThat(createArgs).contains("--tenantTag", tenantTag);
        assertThat(createArgs).contains("--variable", "var1=value1");
        assertThat(createArgs).contains("--variable", "var2=value2");
        assertThat(createArgs).contains("--progress");
        assertThat(createArgs).contains("--deploymentTimeout", deploymentTimeout);
        assertThat(createArgs).doesNotContain("--cancelOnTimeout"); // False, so not included
        assertThat(createArgs).contains("--debug");
    }

    /**
     * Testable wrapper that exposes the protected execute method for testing
     */
    private static class TestableLegacyCliWrapper extends LegacyCliWrapper {
        TestableLegacyCliWrapper(String toolId, FilePath workspace, Launcher launcher,
                                 EnvVars environment, TaskListener listener,
                                 String serverUrl, String apiKey, String spaceId,
                                 String projectName, boolean verboseLogging, boolean ignoreSslErrors) {
            super(toolId, workspace, launcher, environment, listener,
                    serverUrl, apiKey, spaceId, projectName, verboseLogging, ignoreSslErrors);
        }

        @Override
        protected CliExecutionResult execute(List<String> args, Set<Integer> maskedIndices)
                throws IOException, InterruptedException {
            // This will be spied on in tests
            return super.execute(args, maskedIndices);
        }
    }
}
