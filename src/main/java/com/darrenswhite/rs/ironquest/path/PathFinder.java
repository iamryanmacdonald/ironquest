package com.darrenswhite.rs.ironquest.path;

import com.darrenswhite.rs.ironquest.action.Action;
import com.darrenswhite.rs.ironquest.action.LampAction;
import com.darrenswhite.rs.ironquest.player.Player;
import com.darrenswhite.rs.ironquest.player.QuestEntry;
import com.darrenswhite.rs.ironquest.player.QuestPriority;
import com.darrenswhite.rs.ironquest.player.Skill;
import com.darrenswhite.rs.ironquest.quest.Quest;
import com.darrenswhite.rs.ironquest.quest.QuestAccessFilter;
import com.darrenswhite.rs.ironquest.quest.QuestTypeFilter;
import com.darrenswhite.rs.ironquest.quest.Quests;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * Service for finding optimal {@link Path} for a given set of attributes.
 *
 * @author Darren S. White
 */
@Service
public class PathFinder {

  private static final Logger LOG = LogManager.getLogger(PathFinder.class);

  private final Quests quests;

  /**
   * Create a new {@link PathFinder}.
   *
   * @param questsResource the resource to retrieve quest data from
   * @param objectMapper an {@link ObjectMapper}
   */
  @Autowired
  public PathFinder(@Value("${quests.resource}") Resource questsResource, ObjectMapper objectMapper)
      throws IOException {
    this.quests = loadQuests(questsResource, objectMapper);
  }

  public Path find(String name, QuestAccessFilter accessFilter, boolean ironman,
      boolean recommended, Set<Skill> lampSkills, Map<Integer, QuestPriority> questPriorities,
      QuestTypeFilter typeFilter) {
    LOG.debug("Using player profile: " + name);

    Set<QuestEntry> questEntries = quests
        .createQuestEntries(questPriorities, accessFilter, typeFilter);
    Player player = new Player.Builder().setName(name).setIronman(ironman)
        .setRecommended(recommended).setLampSkills(lampSkills).setQuests(questEntries).build();

    player.load();

    return find(player);
  }

  private Path find(Player player) {
    Set<Action> actions = new LinkedHashSet<>();
    List<Action> futureActions = new ArrayList<>();
    PathStats stats = createStats(player);

    LOG.debug("Finding optimal quest path for player: {}", player.getName());

    completePlaceholderQuests(player);

    while (!player.getIncompleteQuests().isEmpty()) {
      Optional<QuestEntry> bestQuest = player.getBestQuest(player.getIncompleteQuests());

      if (bestQuest.isPresent()) {
        processQuest(player, actions, futureActions, bestQuest.get());
      } else {
        throw new IllegalStateException("Unable to find best quest");
      }

      processFutureActions(player, actions, futureActions);
    }

    for (Action futureAction : futureActions) {
      LOG.debug("Adding future action: {}", futureAction);

      actions.add(futureAction.copyForPlayer(player));
    }

    return new Path(actions, stats);
  }

  private PathStats createStats(Player player) {
    double percentComplete =
        (double) player.getCompletedQuests().size() / (double) player.getQuests().size() * 100;

    return new PathStats(percentComplete);
  }

  private void processQuest(Player player, Set<Action> actions, List<Action> futureActions,
      QuestEntry bestQuest) {
    Set<Action> newActions = player.completeQuest(bestQuest);

    for (Action newAction : newActions) {
      if (newAction.isFuture()) {
        LOG.debug("Adding future action: {}", newAction);

        futureActions.add(newAction);
      } else {
        LOG.debug("Processing action: {}", newAction);

        newAction.process(player);
        actions.add(newAction.copyForPlayer(player));
      }
    }
  }

  private void processFutureActions(Player player, Set<Action> actions,
      List<Action> futureActions) {
    for (Iterator<Action> iterator = futureActions.iterator(); iterator.hasNext(); ) {
      Action futureAction = iterator.next();

      if (futureAction.meetsRequirements(player)) {
        if (futureAction instanceof LampAction) {
          LampAction lampAction = (LampAction) futureAction;

          futureAction = player
              .createLampAction(lampAction.getQuestEntry(), lampAction.getLampReward());
        }

        LOG.debug("Processing future action: {}", futureAction);

        futureAction.process(player);
        actions.add(futureAction.copyForPlayer(player));
        iterator.remove();
      }
    }
  }

  private Quests loadQuests(Resource questsResource, ObjectMapper objectMapper) throws IOException {
    LOG.debug("Trying to retrieve quests from resource: {}", questsResource);

    return new Quests(
        objectMapper.readValue(questsResource.getInputStream(), new TypeReference<Set<Quest>>() {
        }));
  }

  private void completePlaceholderQuests(Player player) {
    for (QuestEntry entry : player.getIncompleteQuests()) {
      Quest quest = entry.getQuest();

      if (quest.isPlaceholder()) {
        LOG.debug("Processing placeholder quest: {}", quest.getDisplayName());

        Set<Action> newActions = player.completeQuest(entry);

        for (Action newAction : newActions) {
          newAction.process(player);
        }
      }
    }
  }
}
