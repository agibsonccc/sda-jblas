package org.deeplearning4j.rl4j.learning.sync.listener;

import lombok.Getter;
import org.deeplearning4j.rl4j.learning.Learning;
import org.deeplearning4j.rl4j.learning.listener.TrainingEpochEndEvent;
import org.deeplearning4j.rl4j.learning.listener.TrainingListenerList;
import org.deeplearning4j.rl4j.util.IDataManager;

/**
 * The definition of the event sent by {@link TrainingListenerList#notifyEpochFinished(TrainingEpochEndEvent)}
 * in the context of sync training
 */
public class SyncTrainingEpochEndEvent extends SyncTrainingEvent implements TrainingEpochEndEvent {

    /**
     * The stats of the epoch training
     */
    @Getter
    private final IDataManager.StatEntry statEntry;

    /**
     * @param learning The source of the event
     * @param statEntry The stats of the epoch training
     */
    public SyncTrainingEpochEndEvent(Learning learning, IDataManager.StatEntry statEntry) {
        super(learning);
        this.statEntry = statEntry;
    }
}
