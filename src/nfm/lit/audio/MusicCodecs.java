package nfm.lit.audio;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

import fallk.logmaster.HLogger;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/**
 * Decodes the game's music for the 3D sound, without BASS: the tracker
 * modules of the stages and menus through libopenmpt (BSD), MP3 through the
 * mpg123 and Ogg Vorbis through the vorbisfile it ships with, and WAV through
 * Java Sound. Every decoder gives 32-bit float stereo and loops for ever, as
 * BASS did: a module at its own loop point, a recording from its start.
 * <p>
 * The libraries are libopenmpt's own Windows build, in lib/dlls/win64
 * (another folder with -Dnfm.openmpt); without them, or on another system,
 * the music plays through BASS as before.
 */
final class MusicCodecs {

    private MusicCodecs() {
    }

    /** A loaded piece of music, decoded as it plays: on the sound thread only. */
    interface Decoder extends AutoCloseable {

        /** Samples per second, per channel. */
        int rate();

        /**
         * The level it plays at, matching what BASS played: libopenmpt
         * renders a module as loud as BASS played it, at 0.8; a recording
         * BASS played at 0.8 of its own level.
         */
        float level();

        /** Writes exactly this many frames of interleaved float stereo, looping at the end. */
        void read(MemorySegment out, int frames);

        @Override
        void close();
    }

    /*
     * The modules' stereo separation in percent: 100 is the hard left and
     * right of an Amiga, which tires the ears on headphones; 70 keeps the
     * width and softens it. It is a setting (the "music" command).
     */
    static final int DEFAULT_SEPARATION = 70;
    private static volatile int separation = DEFAULT_SEPARATION;

    static void setSeparation(int percent) {
        separation = Math.max(0, Math.min(200, percent));
    }

    static int separation() {
        return separation;
    }

    private static Natives natives;
    private static boolean tried;

    /** True when the libraries are here and loaded: the 3D sound can then play the music itself. */
    static synchronized boolean available() {
        if (!tried) {
            tried = true;
            natives = Natives.load();
        }
        return natives != null;
    }

    /** A tracker module, from its bytes (it may come out of a zip). */
    static Decoder module(String name, byte[] data, int rate) throws IOException {
        if (!available()) {
            throw new IOException("libopenmpt is not loaded");
        }
        return new ModuleDecoder(natives, name, data, rate);
    }

    /** An MP3, Ogg Vorbis or WAV recording, from its file. */
    static Decoder recording(File file) throws IOException {
        String name = file.getName().toLowerCase(java.util.Locale.ROOT);
        if (name.endsWith(".wav") || name.endsWith(".aif") || name.endsWith(".aiff")) {
            return new JavaSoundDecoder(file);
        }
        if (!available()) {
            throw new IOException("libopenmpt is not loaded");
        }
        if (name.endsWith(".ogg")) {
            return new VorbisDecoder(natives, file);
        }
        return new Mpg123Decoder(natives, file);
    }

    /* --------------------------------------------------------- the libraries */

    private static final class Natives {
        final MethodHandle moduleCreate;
        final MethodHandle moduleDestroy;
        final MethodHandle moduleRepeat;
        final MethodHandle moduleRenderParam;
        final MethodHandle moduleCtlBoolean;
        final MethodHandle moduleRead;
        final MethodHandle freeString;
        final MemorySegment logSilent;
        final MemorySegment errorIgnore;
        final MethodHandle mpgNew;
        final MethodHandle mpgDelete;
        final MethodHandle mpgFormatNone;
        final MethodHandle mpgFormat;
        final MethodHandle mpgOpen;
        final MethodHandle mpgClose;
        final MethodHandle mpgGetFormat;
        final MethodHandle mpgRead;
        final MethodHandle mpgSeek;
        final MethodHandle ovOpen;
        final MethodHandle ovInfo;
        final MethodHandle ovReadFloat;
        final MethodHandle ovSeek;
        final MethodHandle ovClear;

        private Natives(SymbolLookup openmpt, SymbolLookup mpg123, SymbolLookup vorbis) throws Throwable {
            Linker l = Linker.nativeLinker();
            moduleCreate = handle(l, openmpt, "openmpt_module_create_from_memory2", FunctionDescriptor.of(ADDRESS,
                    ADDRESS, JAVA_LONG, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
            moduleDestroy = handle(l, openmpt, "openmpt_module_destroy", FunctionDescriptor.ofVoid(ADDRESS));
            moduleRepeat = handle(l, openmpt, "openmpt_module_set_repeat_count",
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
            moduleRenderParam = handle(l, openmpt, "openmpt_module_set_render_param",
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT));
            moduleCtlBoolean = handle(l, openmpt, "openmpt_module_ctl_set_boolean",
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));
            moduleRead = handle(l, openmpt, "openmpt_module_read_interleaved_float_stereo",
                    FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_INT, JAVA_LONG, ADDRESS));
            freeString = handle(l, openmpt, "openmpt_free_string", FunctionDescriptor.ofVoid(ADDRESS));
            logSilent = openmpt.find("openmpt_log_func_silent").orElseThrow();
            errorIgnore = openmpt.find("openmpt_error_func_ignore").orElseThrow();
            // mpg123's long is 32 bits on Windows
            mpgNew = handle(l, mpg123, "mpg123_new", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));
            mpgDelete = handle(l, mpg123, "mpg123_delete", FunctionDescriptor.ofVoid(ADDRESS));
            mpgFormatNone = handle(l, mpg123, "mpg123_format_none", FunctionDescriptor.of(JAVA_INT, ADDRESS));
            mpgFormat = handle(l, mpg123, "mpg123_format",
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT));
            mpgOpen = handle(l, mpg123, "mpg123_open", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
            mpgClose = handle(l, mpg123, "mpg123_close", FunctionDescriptor.of(JAVA_INT, ADDRESS));
            mpgGetFormat = handle(l, mpg123, "mpg123_getformat",
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
            mpgRead = handle(l, mpg123, "mpg123_read",
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG, ADDRESS));
            mpgSeek = handle(l, mpg123, "mpg123_seek64",
                    FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_LONG, JAVA_INT));
            // vorbisfile's long is 32 bits on Windows too
            ovOpen = handle(l, vorbis, "ov_fopen", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
            ovInfo = handle(l, vorbis, "ov_info", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT));
            ovReadFloat = handle(l, vorbis, "ov_read_float",
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS));
            ovSeek = handle(l, vorbis, "ov_pcm_seek", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG));
            ovClear = handle(l, vorbis, "ov_clear", FunctionDescriptor.of(JAVA_INT, ADDRESS));
        }

        private static MethodHandle handle(Linker l, SymbolLookup lib, String name, FunctionDescriptor fd) {
            return l.downcallHandle(lib.find(name).orElseThrow(() -> new IllegalStateException("no " + name)), fd);
        }

        /*
         * libopenmpt.dll finds its helpers by name, and Windows looks for
         * them beside java.exe, not beside it: loaded first, by path, they
         * are found already in memory.
         */
        static Natives load() {
            Path dir = Path.of(System.getProperty("nfm.openmpt", "lib/dlls/win64"));
            Path main = dir.resolve("libopenmpt.dll");
            if (!System.getProperty("os.name", "").startsWith("Windows") || !Files.isRegularFile(main)) {
                HLogger.info("Music: no libopenmpt (" + main + "), so the music plays through BASS");
                return null;
            }
            try {
                Arena a = Arena.global();
                SymbolLookup.libraryLookup(dir.resolve("openmpt-zlib.dll"), a);
                SymbolLookup.libraryLookup(dir.resolve("openmpt-ogg.dll"), a);
                SymbolLookup vorbis = SymbolLookup.libraryLookup(dir.resolve("openmpt-vorbis.dll"), a);
                SymbolLookup mpg123 = SymbolLookup.libraryLookup(dir.resolve("openmpt-mpg123.dll"), a);
                SymbolLookup openmpt = SymbolLookup.libraryLookup(main, a);
                return new Natives(openmpt, mpg123, vorbis);
            } catch (Throwable ex) {
                HLogger.warn("Music: libopenmpt would not load, so the music plays through BASS: " + ex);
                return null;
            }
        }
    }

    private static RuntimeException failed(String what, Throwable ex) {
        return ex instanceof RuntimeException r ? r : new IllegalStateException(what, ex);
    }

    /* ----------------------------------------------------------- the modules */

    private static final int RENDER_STEREOSEPARATION_PERCENT = 2;
    private static final int RENDER_INTERPOLATIONFILTER_LENGTH = 3;

    private static final class ModuleDecoder implements Decoder {
        private final Natives n;
        private final Arena arena = Arena.ofConfined();
        private final MemorySegment module;
        private final int rate;
        private int separationApplied = -1;

        ModuleDecoder(Natives n, String name, byte[] data, int rate) throws IOException {
            this.n = n;
            this.rate = rate;
            try {
                MemorySegment file = arena.allocate(Math.max(1, data.length));
                MemorySegment.copy(data, 0, file, JAVA_BYTE, 0, data.length);
                MemorySegment error = arena.allocate(JAVA_INT);
                MemorySegment message = arena.allocate(ADDRESS);
                MemorySegment m = (MemorySegment) n.moduleCreate.invokeExact(file, (long) data.length, n.logSilent,
                        MemorySegment.NULL, n.errorIgnore, MemorySegment.NULL, error, message, MemorySegment.NULL);
                if (m.equals(MemorySegment.NULL)) {
                    MemorySegment text = message.get(ADDRESS, 0);
                    String why = text.equals(MemorySegment.NULL) ? "error " + error.get(JAVA_INT, 0)
                            : text.reinterpret(1024).getString(0);
                    if (!text.equals(MemorySegment.NULL)) {
                        n.freeString.invokeExact(text);
                    }
                    throw new IOException("libopenmpt cannot read " + name + ": " + why);
                }
                module = m;
                int ignored = (int) n.moduleRepeat.invokeExact(module, -1); // its own loop, for ever
                // the 8-tap filter, not an Amiga's: the same notes, cleaner
                ignored = (int) n.moduleRenderParam.invokeExact(module, RENDER_INTERPOLATIONFILTER_LENGTH, 8);
                ignored = (int) n.moduleCtlBoolean.invokeExact(module,
                        arena.allocateFrom("render.resampler.emulate_amiga"), 0);
            } catch (IOException ex) {
                arena.close();
                throw ex;
            } catch (Throwable ex) {
                arena.close();
                throw new IOException("libopenmpt cannot read " + name, ex);
            }
        }

        @Override
        public int rate() {
            return rate;
        }

        @Override
        public float level() {
            return 1F;
        }

        @Override
        public void read(MemorySegment out, int frames) {
            try {
                int s = separation;
                if (s != separationApplied) {
                    separationApplied = s;
                    int ignored = (int) n.moduleRenderParam.invokeExact(module, RENDER_STEREOSEPARATION_PERCENT, s);
                }
                long done = 0;
                while (done < frames) {
                    long got = (long) n.moduleRead.invokeExact(module, rate, frames - done,
                            out.asSlice(done * 8L));
                    if (got <= 0) {
                        out.asSlice(done * 8L, (frames - done) * 8L).fill((byte) 0);
                        return;
                    }
                    done += got;
                }
            } catch (Throwable ex) {
                throw failed("libopenmpt", ex);
            }
        }

        @Override
        public void close() {
            try {
                n.moduleDestroy.invokeExact(module);
            } catch (Throwable ex) {
                HLogger.warn("libopenmpt: " + ex);
            }
            arena.close();
        }
    }

    /* ------------------------------------------------------------------ MP3 */

    private static final int MPG123_OK = 0;
    private static final int MPG123_NEW_FORMAT = -11;
    private static final int MPG123_DONE = -12;
    private static final int MPG123_STEREO = 2;
    private static final int MPG123_ENC_FLOAT_32 = 0x200;
    private static final int[] MPG123_RATES = {8000, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000};

    private static final class Mpg123Decoder implements Decoder {
        private final Natives n;
        private final Arena arena = Arena.ofConfined();
        private final MemorySegment handle;
        private final MemorySegment done;
        private final int rate;

        Mpg123Decoder(Natives n, File file) throws IOException {
            this.n = n;
            try {
                MemorySegment error = arena.allocate(JAVA_INT);
                handle = (MemorySegment) n.mpgNew.invokeExact(MemorySegment.NULL, error);
                if (handle.equals(MemorySegment.NULL)) {
                    throw new IOException("mpg123 would not start: error " + error.get(JAVA_INT, 0));
                }
                done = arena.allocate(JAVA_LONG);
                // always float stereo: a mono file comes out on both sides
                int ignored = (int) n.mpgFormatNone.invokeExact(handle);
                for (int rate : MPG123_RATES) {
                    ignored = (int) n.mpgFormat.invokeExact(handle, rate, MPG123_STEREO, MPG123_ENC_FLOAT_32);
                }
                if ((int) n.mpgOpen.invokeExact(handle, arena.allocateFrom(file.getAbsolutePath())) != MPG123_OK) {
                    n.mpgDelete.invokeExact(handle);
                    throw new IOException("mpg123 cannot open " + file);
                }
                MemorySegment rateOut = arena.allocate(JAVA_INT);
                MemorySegment channels = arena.allocate(JAVA_INT);
                MemorySegment encoding = arena.allocate(JAVA_INT);
                if ((int) n.mpgGetFormat.invokeExact(handle, rateOut, channels, encoding) != MPG123_OK) {
                    closeHandle();
                    throw new IOException("mpg123 cannot read " + file);
                }
                rate = rateOut.get(JAVA_INT, 0);
            } catch (IOException ex) {
                arena.close();
                throw ex;
            } catch (Throwable ex) {
                arena.close();
                throw new IOException("mpg123 cannot read " + file, ex);
            }
        }

        @Override
        public int rate() {
            return rate;
        }

        @Override
        public float level() {
            return 0.8F;
        }

        @Override
        public void read(MemorySegment out, int frames) {
            try {
                long want = frames * 8L;
                long have = 0;
                boolean rewound = false;
                while (have < want) {
                    int r = (int) n.mpgRead.invokeExact(handle, out.asSlice(have), want - have, done);
                    long got = done.get(JAVA_LONG, 0);
                    have += got;
                    if (got > 0) {
                        rewound = false;
                    }
                    if (r == MPG123_DONE && !rewound) {
                        long ignored = (long) n.mpgSeek.invokeExact(handle, 0L, 0); // loop from the start
                        rewound = true;
                    } else if (r != MPG123_OK && r != MPG123_NEW_FORMAT && got == 0) {
                        break; // nothing more to be had: silence
                    }
                }
                if (have < want) {
                    out.asSlice(have, want - have).fill((byte) 0);
                }
            } catch (Throwable ex) {
                throw failed("mpg123", ex);
            }
        }

        private void closeHandle() throws Throwable {
            int ignored = (int) n.mpgClose.invokeExact(handle);
            n.mpgDelete.invokeExact(handle);
        }

        @Override
        public void close() {
            try {
                closeHandle();
            } catch (Throwable ex) {
                HLogger.warn("mpg123: " + ex);
            }
            arena.close();
        }
    }

    /* ----------------------------------------------------------- Ogg Vorbis */

    /* OggVorbis_File is under 1 KB on 64-bit Windows; room to spare */
    private static final long OGG_VORBIS_FILE_SIZE = 4096;

    private static final class VorbisDecoder implements Decoder {
        private final Natives n;
        private final Arena arena = Arena.ofConfined();
        private final MemorySegment vf;
        private final MemorySegment pcm;
        private final MemorySegment section;
        private final int rate;
        private final int channels;

        VorbisDecoder(Natives n, File file) throws IOException {
            this.n = n;
            try {
                vf = arena.allocate(OGG_VORBIS_FILE_SIZE, 16);
                if ((int) n.ovOpen.invokeExact(arena.allocateFrom(file.getAbsolutePath()), vf) != 0) {
                    throw new IOException("vorbisfile cannot open " + file);
                }
                MemorySegment info = ((MemorySegment) n.ovInfo.invokeExact(vf, -1)).reinterpret(16);
                channels = info.get(JAVA_INT, 4);
                rate = info.get(JAVA_INT, 8);
                pcm = arena.allocate(ADDRESS);
                section = arena.allocate(JAVA_INT);
            } catch (IOException ex) {
                arena.close();
                throw ex;
            } catch (Throwable ex) {
                arena.close();
                throw new IOException("vorbisfile cannot read " + file, ex);
            }
        }

        @Override
        public int rate() {
            return rate;
        }

        @Override
        public float level() {
            return 0.8F;
        }

        @Override
        public void read(MemorySegment out, int frames) {
            try {
                int have = 0;
                boolean rewound = false;
                // Vorbis keeps left first; with three channels or five and more, right is the third
                int right = channels == 1 ? 0 : channels == 3 || channels >= 5 ? 2 : 1;
                while (have < frames) {
                    int got = (int) n.ovReadFloat.invokeExact(vf, pcm, frames - have, section);
                    if (got <= 0) {
                        if (got == 0 && !rewound) {
                            int ignored = (int) n.ovSeek.invokeExact(vf, 0L);
                            rewound = true;
                            continue;
                        }
                        break;
                    }
                    rewound = false;
                    MemorySegment chans = pcm.get(ADDRESS, 0).reinterpret(8L * channels);
                    MemorySegment l = chans.getAtIndex(ADDRESS, 0).reinterpret(4L * got);
                    MemorySegment r = chans.getAtIndex(ADDRESS, right).reinterpret(4L * got);
                    for (int i = 0; i < got; i++) {
                        out.setAtIndex(JAVA_FLOAT, 2L * (have + i), l.getAtIndex(JAVA_FLOAT, i));
                        out.setAtIndex(JAVA_FLOAT, 2L * (have + i) + 1, r.getAtIndex(JAVA_FLOAT, i));
                    }
                    have += got;
                }
                if (have < frames) {
                    out.asSlice(have * 8L, (frames - have) * 8L).fill((byte) 0);
                }
            } catch (Throwable ex) {
                throw failed("vorbisfile", ex);
            }
        }

        @Override
        public void close() {
            try {
                int ignored = (int) n.ovClear.invokeExact(vf);
            } catch (Throwable ex) {
                HLogger.warn("vorbisfile: " + ex);
            }
            arena.close();
        }
    }

    /* ------------------------------------------------------------ WAV, AIFF */

    private static final class JavaSoundDecoder implements Decoder {
        private static final ValueLayout.OfShort SAMPLE = JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN)
                .withByteAlignment(1);
        private final File file;
        private final int rate;
        private final int channels;
        private AudioInputStream in;
        private byte[] bytes = new byte[0];

        JavaSoundDecoder(File file) throws IOException {
            this.file = file;
            in = open();
            rate = (int) in.getFormat().getSampleRate();
            channels = in.getFormat().getChannels();
        }

        private AudioInputStream open() throws IOException {
            try {
                InputStream raw = new BufferedInputStream(Files.newInputStream(file.toPath()));
                AudioInputStream source = AudioSystem.getAudioInputStream(raw);
                AudioFormat f = source.getFormat();
                AudioFormat pcm = new AudioFormat(f.getSampleRate(), 16, f.getChannels(), true, false);
                return AudioSystem.getAudioInputStream(pcm, source);
            } catch (javax.sound.sampled.UnsupportedAudioFileException | IllegalArgumentException ex) {
                throw new IOException("Java Sound cannot read " + file, ex);
            }
        }

        @Override
        public int rate() {
            return rate;
        }

        @Override
        public float level() {
            return 0.8F;
        }

        @Override
        public void read(MemorySegment out, int frames) {
            int frameBytes = 2 * channels;
            if (bytes.length < frames * frameBytes) {
                bytes = new byte[frames * frameBytes];
            }
            int have = 0;
            boolean rewound = false;
            try {
                while (have < frames) {
                    int got = in.readNBytes(bytes, 0, (frames - have) * frameBytes) / frameBytes;
                    if (got <= 0) {
                        if (rewound) {
                            break;
                        }
                        in.close();
                        in = open(); // loop from the start
                        rewound = true;
                        continue;
                    }
                    rewound = false;
                    MemorySegment b = MemorySegment.ofArray(bytes);
                    for (int i = 0; i < got; i++) {
                        float l = b.get(SAMPLE, (long) i * frameBytes) / 32768F;
                        float r = channels == 1 ? l : b.get(SAMPLE, (long) i * frameBytes + 2) / 32768F;
                        out.setAtIndex(JAVA_FLOAT, 2L * (have + i), l);
                        out.setAtIndex(JAVA_FLOAT, 2L * (have + i) + 1, r);
                    }
                    have += got;
                }
            } catch (IOException ex) {
                HLogger.warn("Music: " + ex);
            }
            if (have < frames) {
                out.asSlice(have * 8L, (frames - have) * 8L).fill((byte) 0);
            }
        }

        @Override
        public void close() {
            try {
                in.close();
            } catch (IOException ex) {
                // closing anyway
            }
        }
    }
}
