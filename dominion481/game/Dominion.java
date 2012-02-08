package dominion481.game;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

public class Dominion {
   List<Class<? extends Player>> playerClasses;
   List<Player> players;
   List<Card> cardSet;
   DominionState state;

   public Dominion(List<Class<? extends Player>> players, List<Card> cardSet) {
      this.playerClasses = players;
      this.cardSet = cardSet;
   }

   public List<Player> play() throws InvocationTargetException,
         IllegalAccessException, InstantiationException, NoSuchMethodException {
      players = new ArrayList<Player>();
      state = new DominionState(players, cardSet);
      
      for (Class<? extends Player> cls : playerClasses) {
         players.add(cls.getConstructor(state.getClass(), Long.class).newInstance(state, 0));
      }

      while (true) {
         for (Player p : players) {
            p.actionPhase();
            if (state.isGameOver()) {
               return getWinners();
            }
         }
      }

      // return null;
   }

   public List<Player> getWinners() {
      int max = -1;
      List<Player> maxPs = null;

      for (Player p : players) {
         int points = p.getVictoryPoints();
         if (points > max) {
            maxPs = new ArrayList<Player>();
            max = points;
         }

         if (points >= max) {
            maxPs.add(p);
         }
      }

      return maxPs;
   }

   public static void main(String[] args) {

   }
}
