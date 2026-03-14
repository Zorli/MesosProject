package it.polimi.ingsoft.mesos.server.model.enums;

public enum BenefitType {
    POST_DRAW_SET_FOR_FOOD,                             // <-- done
    POST_DRAW_PAIR_OF_INVENTOR_WITH_SAME_INVENTION,     // <-- done
    END_ROUND_EXTRA_CARD,                               // <-- TODO but we use it in the end round phase
    END_ROUND_TOTEM_POSITION,                           // <-- TODO but we use it in the end round phase

    // We just have to call the applyEffect on the event cards and give them the player list.
    EVENT_BONUS_SUSTENANCE_DISCOUNT_FOR_TYPE,           // <-- done
    EVENT_BONUS_LOSS_FROM_SHAMAN_EVENT,                 // <-- done
    EVENT_BONUS_SHAMAN_WIN_MULTIPLIER,                  // <-- done
    EVENT_BONUS_STARS_SHAMAN,                           // <-- done
    EVENT_BONUS_HUNTER,                                 // <-- done
    EVENT_BONUS_CAVE_PAINTING,                          // <-- done

    FINAL_PRESTIGE_POINTS_FOR_SET,                      // TODO
    FINAL_BUILDER_TOTAL_PP_MULTIPLIER,                  // TODO
    FINAL_BONUS_MULTIPLIER_FOR_TYPE,                    // TODO
    FINAL_BONUS_BIG_PP_REWARD,                          // TODO
}
