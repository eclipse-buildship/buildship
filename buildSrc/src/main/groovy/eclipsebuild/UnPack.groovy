package eclipsebuild

import com.google.common.hash.HashingOutputStream
import org.gradle.api.artifacts.transform.*
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.internal.io.NullOutputStream

import java.nio.file.Files

import static com.google.common.hash.Hashing.sha256

@CacheableTransform
abstract class UnPack implements TransformAction<TransformParameters.None> {
    @InputArtifact
    abstract Provider<FileSystemLocation> getInputArtifact()

    @Override
    void transform(TransformOutputs outputs) {
        def input = inputArtifact.get().asFile
        def targetDir = outputs.dir("${input.name}.${ARTIFACT_TYPE_NAME}")
        verifySdk256Sum(input)
        unpack(input, targetDir)
        new File(targetDir, Constants.eclipseExePath).setExecutable(true)
    }

    private void verifySdk256Sum(File destination) {
        def actualSha256Sum = calculateSdk256Sum(destination)
        if (actualSha256Sum != Constants.eclipseSdkDownloadSha256Hash) {
            throw new RuntimeException("Eclipse SDK SHA-126 sum does not match. Expected: $Constants.eclipseSdkDownloadSha256Hash, actual: $actualSha256Sum.")
        }
    }

    private static String calculateSdk256Sum(File destination) {
        def hashingStream = new HashingOutputStream(sha256(), new NullOutputStream())
        Files.copy(destination.toPath(), hashingStream)
        return hashingStream.hash().toString()
    }

    abstract void unpack(File zipFile, File targetDir)

    public final static ARTIFACT_TYPE_NAME = "unpacked"
}
