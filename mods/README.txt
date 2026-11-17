Mods for Need For Madness: LIT
==============================

A mod is a folder in here that mirrors the game's data folder. A file in it
replaces the game's file of the same name:

  mods\<your mod>\models\<car or track piece>.rad   replaces it in data\models.radq
  mods\<your mod>\images\<image>.gif                replaces it in data\images.radq
  mods\<your mod>\sounds\<sound>.wav                replaces it in data\sounds.radq
  mods\<your mod>\stages\nfm2\3.txt                 replaces data\stages\nfm2\3.txt
  mods\<your mod>\music\nfm2\stage3.ogg             replaces stage 3's music, whatever
                                                    format the game's own is in
                                                    (.radq, .zip, .zipo, .ogg, .mp3,
                                                    .wav or .mid)

Files the game does not have are listed in the log and left alone: a mod
replaces, it does not add.

Load order: mods apply in the order load-order.txt lists them, one folder
name per line; where two replace the same file, the later one wins. Put a -
in front of a name to turn that mod off, and # before a comment. Folders the
file does not list come after the listed ones, in alphabetical order.

Example load-order.txt:

  better-sounds
  my-cars          # wins over better-sounds where both change a file
  -night-stages    # off for now

In the developer console, "mods" lists what applies, and "mods reload" reads
this folder again: models when the game is next at the main menu, stages and
music with the next race, images and sounds the next time the game starts.

A replay notes the mods it was recorded with; one recorded with
different models may not play back the same. Test runs (verify, record,
autotest) leave mods out unless started with -Dnfm.mods=on.
