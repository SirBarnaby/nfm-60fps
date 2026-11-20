package nfm.lit;

/**

 Author: G6
 
 **/
public class GameFacts
{
     public static final int numberOfStages = 17; //make sure to change GameUi.loadmusic, GameUi.hipnoload and GameUi.sndsize

     public static final int numberOfCars = 16;

     public static int numberOfPlayers = 7;

     /**
      * The cars people drive: 1, car 0, or 2 in split screen, cars 0 and 1.
      * Set by the game thread as split screen starts and ends, never during
      * a race; the classic game always has 1.
      */
     public static volatile int humans = 1;

     /**
      * The car the computer cars pace themselves on: car 0 in the classic
      * game; in split screen the human further ahead, set once a tick
      * before the computer cars drive.
      */
     public static int paceCar = 0;

     /**
      * The game's own view, 16:9: the race camera is placed in it, and what is
      * on screen in it is what the race reads back (a car out of view finishes
      * a repair hoop at once). Races play out the same in any window.
      */
     public static final int GAME_WIDTH = 1280;

     public static final int GAME_HEIGHT = 720;

     /**
      * The picture the menus and the HUD are drawn in, in its own pixels.
      * With the UI at native size it is the window's pixels divided by the UI
      * scale, so lettering and art keep their own size whatever the monitor;
      * otherwise it is the race's picture, {@link #sceneWidth} x
      * {@link #sceneHeight}. GAME_WIDTH x GAME_HEIGHT in a 1280x720 window.
      * Changed only by the game thread at the start of a tick, with the
      * presenter locked out.
      */
     public static volatile int screenWidth = GAME_WIDTH;

     public static volatile int screenHeight = GAME_HEIGHT;

     /**
      * The race as it is shown, in the game's units: GAME_WIDTH x GAME_HEIGHT
      * at 16:9, wider or taller in a window of another shape (see
      * {@link #pictureShape}), so it fills the window with no bars; the
      * graphics card draws it at the window's own resolution.
      */
     public static volatile int sceneWidth = GAME_WIDTH;

     public static volatile int sceneHeight = GAME_HEIGHT;

     /** The narrowest and the widest shapes the picture takes; beyond them it is shown with bars. */
     private static final float NARROWEST = 5F / 4F;

     private static final float WIDEST = 32F / 9F;

     /** How far right of the menus' corner a 1280x720 screen stands in them: it is in the middle. */
     public static int marginX() {
          return (screenWidth - GAME_WIDTH) / 2;
     }

     /** How far down from the menus' corner a 1280x720 screen stands in them. */
     public static int marginY() {
          return (screenHeight - GAME_HEIGHT) / 2;
     }

     /** How far right of the race picture's corner the game's own 1280x720 view stands. */
     public static int sceneMarginX() {
          return (sceneWidth - GAME_WIDTH) / 2;
     }

     /** How far down from the race picture's corner the game's own 1280x720 view stands. */
     public static int sceneMarginY() {
          return (sceneHeight - GAME_HEIGHT) / 2;
     }

     /**
      * The picture's size for a window {@code width} x {@code height}: the
      * game's own view, widened (it keeps its height) or made taller (it keeps
      * its width) to the window's shape, so the view at 16:9 is always all
      * there and a window of another shape sees more around it. Even sizes,
      * so the picture's centre is a whole pixel.
      */
     public static java.awt.Dimension pictureShape(int width, int height) {
          if (width <= 0 || height <= 0) {
               return new java.awt.Dimension(GAME_WIDTH, GAME_HEIGHT);
          }
          float shape = Math.max(NARROWEST, Math.min(WIDEST, width / (float) height));
          if (shape >= GAME_WIDTH / (float) GAME_HEIGHT) {
               return new java.awt.Dimension(Math.max(GAME_WIDTH, Math.round(GAME_HEIGHT * shape / 2F) * 2),
                         GAME_HEIGHT);
          }
          return new java.awt.Dimension(GAME_WIDTH, Math.max(GAME_HEIGHT, Math.round(GAME_WIDTH / shape / 2F) * 2));
     }

     public static final int polyLimit = 300;
}
