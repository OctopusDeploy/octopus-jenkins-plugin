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
public class CliWrapperTest {

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

    private TestableCliWrapper cliWrapper;

    @BeforeEach
    public void setUp() throws IOException, InterruptedException {
        when(listener.getLogger()).thenReturn(logger);

        cliWrapper = spy(new TestableCliWrapper(
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

        // Verify execute was called twice: once for login, once for pack
        verify(cliWrapper, times(1)).execute(argsCaptor.capture(), maskedCaptor.capture());

        // Get the second call (the pack command)
        List<List<String>> allArgs = argsCaptor.getAllValues();
        List<String> packArgs = allArgs.get(0);

        // Verify pack command structure
        assertThat(packArgs).containsSequence("package", format, "create");
        assertThat(packArgs).contains("--id", packageId);
        assertThat(packArgs).contains("--version", packageVersion);
        assertThat(packArgs).contains("--base-path", sourcePath);
        assertThat(packArgs).contains("--include", "file1.txt");
        assertThat(packArgs).contains("--include", "file2.txt");
        assertThat(packArgs).contains("--out-folder", outputPath);
        assertThat(packArgs).contains("--overwrite");
        assertThat(packArgs).contains("--verbose");
        assertThat(packArgs).contains("--space", "Spaces-1");
        assertThat(packArgs).contains("--no-prompt");
        assertThat(packArgs).contains("--output-format", "json");
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

        // Verify execute was called twice: once for login, once for push
        verify(cliWrapper, times(2)).execute(argsCaptor.capture(), anySet());

        // Get the second call (the push command)
        List<String> pushArgs = argsCaptor.getAllValues().get(1);

        // Verify push command structure
        assertThat(pushArgs).containsSequence("package", "upload");
        assertThat(pushArgs).contains("--package", "/path/to/package1.zip");
        assertThat(pushArgs).contains("--package", "/path/to/package2.zip");
        assertThat(pushArgs).contains("--overwrite-mode", "overwrite");
        assertThat(pushArgs).contains("--timeout", "300");
        assertThat(pushArgs).contains("--space", "Spaces-1");
        assertThat(pushArgs).contains("--no-prompt");
        assertThat(pushArgs).contains("--output-format", "json");
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

        // Verify execute was called twice: once for login, once for pushBuildInformation
        verify(cliWrapper, times(2)).execute(argsCaptor.capture(), anySet());

        // Get the second call (the build-information command)
        List<String> buildInfoArgs = argsCaptor.getAllValues().get(1);

        // Verify build-information command structure
        assertThat(buildInfoArgs).containsSequence("build-information", "upload");
        assertThat(buildInfoArgs).contains("--package-id", "Package1");
        assertThat(buildInfoArgs).contains("--package-id", "Package2");
        assertThat(buildInfoArgs).contains("--version", version);
        assertThat(buildInfoArgs).contains("--file", filePath);
        assertThat(buildInfoArgs).contains("--overwrite-mode", "fail");
        assertThat(buildInfoArgs).contains("--space", "Spaces-1");
        assertThat(buildInfoArgs).contains("--no-prompt");
        assertThat(buildInfoArgs).contains("--output-format", "json");
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

        // Verify execute was called twice: once for login, once for deployRelease
        verify(cliWrapper, times(2)).execute(argsCaptor.capture(), anySet());

        // Get the second call (the deploy command)
        List<String> deployArgs = argsCaptor.getAllValues().get(1);

        // Verify deploy command structure
        assertThat(deployArgs).containsSequence("release", "deploy");
        assertThat(deployArgs).contains("--project", "TestProject");
        assertThat(deployArgs).contains("--version", version);
        assertThat(deployArgs).contains("--environment", environment);
        assertThat(deployArgs).contains("--tenant", tenant);
        assertThat(deployArgs).contains("--tenant-tag", tenantTag);
        assertThat(deployArgs).contains("--variable", "var1=value1");
        assertThat(deployArgs).contains("--variable", "var2=value2");
        assertThat(deployArgs).contains("--force");
        assertThat(deployArgs).contains("--space", "Spaces-1");
        assertThat(deployArgs).contains("--no-prompt");
        assertThat(deployArgs).contains("--output-format", "json");
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

        // Verify execute was called twice: once for login, once for createRelease
        verify(cliWrapper, times(2)).execute(argsCaptor.capture(), anySet());

        // Get the second call (the create release command)
        List<String> createArgs = argsCaptor.getAllValues().get(1);

        // Verify create release command structure
        assertThat(createArgs).containsSequence("release", "create");
        assertThat(createArgs).contains("--project", "TestProject");
        assertThat(createArgs).contains("--version", version);
        assertThat(createArgs).contains("--channel", channel);
        assertThat(createArgs).contains("--release-notes", releaseNotes);
        assertThat(createArgs).contains("--package-version", defaultPackageVersion);
        assertThat(createArgs).contains("--package", "Package1:1.0.1");
        assertThat(createArgs).contains("--package", "Package2:1.0.2");
        assertThat(createArgs).contains("--git-ref", gitRef);
        assertThat(createArgs).contains("--git-commit", gitCommit);
        assertThat(createArgs).contains("--space", "Spaces-1");
        assertThat(createArgs).contains("--no-prompt");
        assertThat(createArgs).contains("--output-format", "json");
    }

    @Test
    public void pack_withMinimalArguments_callsExecuteWithCorrectArguments() throws IOException, InterruptedException {
        // Arrange
        String packageId = "MinimalPackage";
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> argsCaptor = ArgumentCaptor.forClass(List.class);

        // Act
        Result result = cliWrapper.pack(packageId, null, "zip", null, null, null, false, null);

        // Assert
        assertThat(result).isEqualTo(Result.SUCCESS);
        verify(cliWrapper, times(1)).execute(argsCaptor.capture(), anySet());

        List<String> packArgs = argsCaptor.getAllValues().get(0);

        // Verify minimal pack command structure
        assertThat(packArgs).containsSequence("package", "zip", "create");
        assertThat(packArgs).contains("--id", packageId);
        assertThat(packArgs).doesNotContain("--version");
        assertThat(packArgs).doesNotContain("--base-path");
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
        verify(cliWrapper, times(2)).execute(argsCaptor.capture(), anySet());

        List<String> pushArgs = argsCaptor.getAllValues().get(1);

        // Verify overwrite mode is NOT included when null
        assertThat(pushArgs).doesNotContain("--overwrite-mode");
        assertThat(pushArgs).contains("--package", "/path/to/package.zip");
    }

    @Test
    public void execute_isMaskedForLogin() throws IOException, InterruptedException {
        // Arrange
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<Integer>> maskedCaptor = ArgumentCaptor.forClass(Set.class);

        // Act
        cliWrapper.push(Arrays.asList("/path/to/package1.zip", "/path/to/package2.zip"), "OverwriteExisting", "");

        // Assert
        verify(cliWrapper, times(2)).execute(anyList(), maskedCaptor.capture());

        // Get the first call (login command) and verify API key is masked
        List<Set<Integer>> allMasked = maskedCaptor.getAllValues();
        Set<Integer> loginMasked = allMasked.get(0);

        // The login command masks the API key position
        assertThat(loginMasked).isNotEmpty();
    }

    @Test
    public void deployRelease_withWaitForDeployment_callsExecuteForTaskWait() throws IOException, InterruptedException {
        // Arrange
        String version = "1.0.0";
        String environment = "Production";
        String tenant = "TenantA";
        String tenantTag = "Region/US";
        List<String> variables = Arrays.asList("var1=value1");
        boolean waitForDeployment = true;
        String deploymentTimeout = "00:15:30"; // 15 minutes 30 seconds
        boolean cancelOnTimeout = true;
        String additionalArgs = null;

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> argsCaptor = ArgumentCaptor.forClass(List.class);

        // Mock the deploy execute call to return JSON with ServerTaskId
        String deployResultJson = "[{\"ServerTaskId\":\"ServerTasks-12345\"}]";
        doReturn(new CliExecutionResult("", 0))
                .doReturn(new CliExecutionResult(deployResultJson, 0))
                .doReturn(new CliExecutionResult("", 0))
                .when(cliWrapper)
                .execute(anyList(), anySet());

        // Act
        Result result = cliWrapper.deployRelease(version, environment, tenant, tenantTag,
                variables, waitForDeployment, deploymentTimeout, cancelOnTimeout, additionalArgs);

        // Assert
        assertThat(result).isEqualTo(Result.SUCCESS);

        // Verify execute was called three times: login, deploy, task wait
        verify(cliWrapper, times(3)).execute(argsCaptor.capture(), anySet());

        List<List<String>> allArgs = argsCaptor.getAllValues();

        // Verify the first call is login
        List<String> loginArgs = allArgs.get(0);
        assertThat(loginArgs).contains("login");

        // Verify the second call is deploy
        List<String> deployArgs = allArgs.get(1);
        assertThat(deployArgs).containsSequence("release", "deploy");
        assertThat(deployArgs).contains("--project", "TestProject");
        assertThat(deployArgs).contains("--version", version);
        assertThat(deployArgs).contains("--environment", environment);

        // Verify the third call is task wait
        List<String> waitArgs = allArgs.get(2);
        assertThat(waitArgs).containsSequence("task", "wait");
        assertThat(waitArgs).contains("ServerTasks-12345");
        assertThat(waitArgs).contains("--progress"); // 15*60 + 30 = 930 seconds
        assertThat(waitArgs).contains("--timeout", "930"); // 15*60 + 30 = 930 seconds
        assertThat(waitArgs).contains("--cancel-on-timeout");
        assertThat(waitArgs).contains("--space", "Spaces-1");
        assertThat(waitArgs).contains("--output-format", "json");
    }

    @Test
    public void createRelease_withDeployToEnvironment_callsDeployRelease() throws IOException, InterruptedException {
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
        boolean waitForDeployment = false;
        String deploymentTimeout = null;
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

        // Verify execute was called four times:
        // 1. login (from createRelease)
        // 2. create release
        // 3. login (from deployRelease)
        // 4. deploy
        verify(cliWrapper, times(4)).execute(argsCaptor.capture(), anySet());

        List<List<String>> allArgs = argsCaptor.getAllValues();

        // Verify the first call is login
        List<String> loginArgs1 = allArgs.get(0);
        assertThat(loginArgs1).contains("login");

        // Verify the second call is create release
        List<String> createArgs = allArgs.get(1);
        assertThat(createArgs).containsSequence("release", "create");
        assertThat(createArgs).contains("--project", "TestProject");
        assertThat(createArgs).contains("--version", version);
        assertThat(createArgs).contains("--channel", channel);
        assertThat(createArgs).contains("--release-notes", releaseNotes);
        assertThat(createArgs).contains("--package-version", defaultPackageVersion);
        assertThat(createArgs).contains("--package", "Package1:1.0.1");
        assertThat(createArgs).contains("--git-ref", gitRef);
        assertThat(createArgs).contains("--git-commit", gitCommit);
        assertThat(createArgs).contains("--debug");

        // Verify the third call is login (from deployRelease)
        List<String> loginArgs2 = allArgs.get(2);
        assertThat(loginArgs2).contains("login");

        // Verify the fourth call is deploy with all the deployment parameters
        List<String> deployArgs = allArgs.get(3);
        assertThat(deployArgs).containsSequence("release", "deploy");
        assertThat(deployArgs).contains("--project", "TestProject");
        assertThat(deployArgs).contains("--version", version);
        assertThat(deployArgs).contains("--environment", deployToEnvironment);
        assertThat(deployArgs).contains("--tenant", tenant);
        assertThat(deployArgs).contains("--tenant-tag", tenantTag);
        assertThat(deployArgs).contains("--variable", "var1=value1");
        assertThat(deployArgs).contains("--variable", "var2=value2");
        assertThat(deployArgs).contains("--debug");
    }

    /**
     * Testable wrapper that exposes the protected execute method for testing
     */
    private static class TestableCliWrapper extends CliWrapper {
        TestableCliWrapper(String toolId, FilePath workspace, Launcher launcher,
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
