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
      * The game's own view, 16:9: the race camera is placed in it, and what is
      * on screen in it is what the race reads back (a car out of view finishes
      * a repair hoop at once). Races play out the same in any window.
      */
     public static final int GAME_WIDTH = 1280;

     public static final int GAME_HEIGHT = 720;

     /**
      * The picture the game draws, in its own pixels: GAME_WIDTH x GAME_HEIGHT
      * at 16:9, wider or taller in a window of another shape (see
      * {@link #pictureShape}), so the picture fills it with no bars. Changed
      * only by the game thread at the start of a tick, with the presenter
      * locked out.
      */
     public static volatile int screenWidth = GAME_WIDTH;

     public static volatile int screenHeight = GAME_HEIGHT;

     /** The narrowest and the widest shapes the picture takes; beyond them it is shown with bars. */
     public static final float NARROWEST = 5F / 4F;

     public static final float WIDEST = 32F / 9F;

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
