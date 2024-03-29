package com.e.rhino.exercises.content;

import android.util.Log;

import com.e.rhino.R;
import com.e.rhino.RssReader;
import com.e.rhino.Speech;
import com.e.rhino.Tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ExerciseContent {

    public static final int startSeconds = 15;

    /**
     * The array of items from the rss feed
     */
    public static List<ExerciseItem> exerciseList = new ArrayList<ExerciseItem>();

    public ExerciseContent(String url)
    {
        Log.i("parse", "Get Exercises from RSS...");
        RssReader.fetchExerciseList(url, exerciseList);
    }

    public enum eInstructionType {
        none,
        switchLeg,
        switchSide
    }

    private void add(ExerciseItem item)
    {
        // fix order
        item.order = exerciseList.size() + 1;
        exerciseList.add(item);
    }

    public long getTotalSeconds() {
        long totalSeconds = 0;

        return totalSeconds;
    }

    public String getTotalTime() {
        return Tools.getTimeFromSeconds(getTotalSeconds());
    }

    private static void addItem(ExerciseItem item) {
        exerciseList.add(item);
    }

    public boolean isLoaded() {
        return (RssReader.isLoaded());
    }

    /**
     * A program item representing a piece of content.
     */
    public static class ExerciseItem {
        public String name;
        public String description;
        public int order;
        public List<Question> questions = null;

        public ExerciseItem(String name, String description, List<Question> questions) {
            this.name = name;
            this.description = description;
            this.order = order;
            this.questions = questions;
        }

        public boolean isFirst() {
            return this.order <= 1;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static int getRandomIndex(List<ExerciseContent.Question> items) {
        int ix = new Random().nextInt(items.size());
        if (items.get(ix).uses == 0) {
            return ix;
        }

        // find the next unused item
        for (int i = 0; i < items.size(); i++)
        {
            ix++;
            if (ix >= items.size())
                ix = 0;

            if (items.get(ix).uses == 0)
                return ix;
        }

        return -1;
    }

    public static class Question {
        public String question;
        public int questionLanguage = Speech.languageDefault;
        public String answer;
        public int answerLanguage = Speech.languageDefault;
        public int uses = 0;

        public Question(String question) {
            this.question = question;
        }

        @Override
        public String toString() {
            return question;
        }
    }
}