package nfm.lit.gpu;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Run by build.ps1 after compiling the game: compiles every shader in
 * {@link Shaders} with Windows' shader compiler into
 * {@code <classes>/nfm/lit/gpu/shaders}, so they go into madness.jar and the
 * game does not compile them when it starts.
 */
public final class ShaderBaker {

    private ShaderBaker() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: ShaderBaker <classes folder>");
            System.exit(2);
        }
        Path folder = Path.of(args[0], "nfm", "lit", "gpu", HlslCompiler.BAKED_FOLDER);
        HlslCompiler compiler = new HlslCompiler();
        for (Shaders.Source shader : Shaders.ALL) {
            compiler.bake(shader, folder);
        }
        System.out.println("Baked " + Shaders.ALL.size() + " shaders.");
    }
}
