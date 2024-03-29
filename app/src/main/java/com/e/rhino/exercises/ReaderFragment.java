package com.e.rhino.exercises;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.os.Handler;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.e.rhino.R;
import com.e.rhino.Speech;
import com.e.rhino.Tools;
import com.e.rhino.exercises.content.ExerciseContent;

import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class ReaderFragment extends Fragment {

    static private boolean autoStart = false;
    static private boolean started = false;
    private boolean timerPaused = false;
    private boolean finished = false;
    private int secondsRemaining = 0;
    private int secondsRewind = 5;
    private int secondsFastForward = secondsRewind;
    private final int second = 1000; // 1 Second
    private final int countdownSeconds = 5;
    private final int nextCountdownSeconds = 3;
    private int pauseBetweenSentences = 5;
    private final int getReadySeconds = countdownSeconds + 1;
    private Handler handler = new Handler();
    private int currentQuestion = 0;
    private int questionCount = 0;
    private ExerciseContent.Question mAnswerPending = null;

    private Runnable runnable = new Runnable(){
        public void run() {

            secondsRemaining--;
            updateTimerDisplay(secondsRemaining);

            if (secondsRemaining >= 1) {
                handler.postDelayed(runnable, second); // update in 1 second
            }
            else {
                if (mAnswerPending != null) // even number means ask a new question{
                {
                    Speech.utter(mAnswerPending.answer, TextToSpeech.QUEUE_ADD, "answer", mAnswerPending.answerLanguage);
                    mAnswerPending = null; // q and a have been spoken, finished with it
                    setStaticName(); // show the answer
                }
                else {
                    loadNextQuestion();
                }
            }
        }
    };

    private Runnable startUp = new Runnable(){
        public void run() {

            ExercisesActivity activity = (ExercisesActivity) getActivity();
            if (null != activity) {
                if (activity.isLoaded()) {
                    start();
                } else {
                    Log.i("startup", "waiting one second");
                    handler.postDelayed(startUp, second); // update in 1 second
                }
            }
        }
    };

    public ReaderFragment() {
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        stopTimer();
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_reader, container, false);
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Handle the back button event
                Speech.speak("Terminado", TextToSpeech.QUEUE_FLUSH);
                onHardStop();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), callback);

        //todo: moved to service
        /*
        Speech.setCallback(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                // Speaking started.
            }

            @Override
            public void onDone(String utteranceId) {
                // Speaking stopped.
                if (utteranceId.length() > 0)
                {
                    if (!finished && !timerPaused)
                    {
                        if (utteranceId.equals("question"))
                            startTimer(pauseBetweenSentences); // pause after question
                        else
                            startTimer(2); // pause between questions
                    }
                }
            }

            @Override
            public void onError(String utteranceId) {
                // There was an error.
            }
        });
        */

        if (this.started) {
            loadNext();
        }
        else if (this.autoStart) {
            // not used
            handler.postDelayed(this.startUp, this.second * 2);
        }
        else
        {
            // start the reader service
            startService();

            //start();
        }
    }

    private void startService() {
        // starting the service
        getActivity().startService(new Intent(getActivity(), ReaderService.class ) );
    }

    private void stopService() {
        // stopping the service
        getActivity().stopService(new Intent(getActivity(), ReaderService.class));
    }

    public boolean onFabPlayPauseClicked() {
        if (started) {
            if (timerPaused) {
                Speech.speak("Continuado.  ", TextToSpeech.QUEUE_FLUSH);
                startTimer(secondsRemaining); // restart timer
            } else {
                Speech.speak("Pausado.  ", TextToSpeech.QUEUE_FLUSH);
                stopTimer();
            }

            timerPaused = !timerPaused;
        }
        else
        {
            start();
        }

        return timerPaused;
    }

    public boolean onFabNextClicked() {
        if (started) {
            //timerPaused = false;
            loadNextQuestion();
        }
        else {
            //start();
        }

        return timerPaused;
    }

    public boolean onFabPreviousClicked() {

        if (started) {
            if (timerPaused) {
                Speech.speak("Resuming...  ", TextToSpeech.QUEUE_FLUSH);
                startTimer(nextCountdownSeconds); // restart timer
                timerPaused = false;
            } else {
                int seconds = nextCountdownSeconds + 1;
                if (secondsRemaining > seconds)
                    secondsRemaining = seconds; // countdown from 3
                else
                    secondsRemaining = 1; // do it now
            }
        } else {
            start();
        }

        ((ExercisesActivity) getActivity()).setFabPlayIcon(timerPaused);

        return timerPaused;
    }

    public void onFabRewindClicked() {
        this.secondsRemaining += this.secondsRewind;
        if (timerPaused)
            updateTimerDisplay(secondsRemaining);
    }

    private long mStartTime;
    private String mTotalTime = "";
    private void start() {
        ExercisesActivity activity = (ExercisesActivity) getActivity();
        if (null != activity) {
            if (activity.isLoaded()) {
                Speech.speak("Empezando", TextToSpeech.QUEUE_ADD);
                this.started = true;
                activity.reset();
                mStartTime = System.currentTimeMillis();
                loadNext();
            } else {
                Speech.speak("Wait for exercises to finish loading...", TextToSpeech.QUEUE_ADD);
                handler.postDelayed(this.startUp, this.second); // wait 1 second
            }
        }
    }

    public void onHardStop() {
        this.started = false;
        this.finished = true;
        stopService();
        stopTimer();
        loadFragment("StartFragment");
    }

    private void loadNext() {
        ExercisesActivity activity = (ExercisesActivity) getActivity();
        if (null == activity)
            return;

        ExerciseContent.ExerciseItem exerciseItem = activity.getNextExercise();

        if (null != exerciseItem)
        {
            //resetQuestions(exerciseItem.questions);
            Speech.speak("Comenzando el capítulo: " + exerciseItem.name, TextToSpeech.QUEUE_ADD);

            int seconds = activity.getTimerSeconds();
            String title = "";
            String text = "";

            this.questionCount = 0;
            loadNextQuestion();
            activity.setFabPlayIcon(false);
        }
        else {
            // end
            stopTimer();
            activity.setFabPlayIcon(true);
            activity.setRunTime(getElapsedTime());
            Speech.shutup();
            loadFragment("FinishedFragment");
        }
    }

    private String getElapsedTime()
    {
        long seconds = (System.currentTimeMillis() - mStartTime) / 1000;
        return Tools.getTimeFromSeconds(seconds);
    }

    private void resetQuestions(List<ExerciseContent.Question> questions)
    {
        if (null != questions) {
            Iterator<ExerciseContent.Question> iterator = questions.iterator();
            while (iterator.hasNext()) {
                ExerciseContent.Question question = iterator.next();
                question.uses = 0;
            }
        }
    }

    public void loadNextQuestion()
    {
        ExercisesActivity activity = (ExercisesActivity) getActivity();
        if (null == activity)
            return;

        ExerciseContent.ExerciseItem exerciseItem = activity.getCurrentExercise();
        List<ExerciseContent.Question> questions = exerciseItem.questions;

        // read it
        boolean questionFound = false;
        if (null != questions) {
            if (read(questions)) {
                setStaticViews(exerciseItem);
                questionFound = true;
            }
        }

        if (!questionFound)
        {
            this.finished = true;
            //Speech.speak("Fin del capítulo.", TextToSpeech.QUEUE_ADD);
            loadNext();
        }
        else
        {
            this.finished = false;
        }
    }

    private boolean read(List<ExerciseContent.Question> questions) {
        boolean rc = false;
        int randomIndex = ExerciseContent.getRandomIndex(questions);
        if (randomIndex >= 0) {
            currentQuestion = randomIndex;
            questionCount++;
            ExerciseContent.Question question = questions.get(randomIndex);
            question.uses++;
            if (!this.timerPaused) {

                // calculate how time will be needed to answer
                int seconds = 3;
                String[] words = question.question.split(" ");
                if (words.length >= 8)
                {
                    seconds = words.length / 2; // use 2 words per second
                    seconds = Tools.keepInRange(seconds, 5, 7);
                }

                this.pauseBetweenSentences = seconds;

                if (question.answer != null) {
                    // if there is an answer, then save for the end of the timer
                    this.mAnswerPending = question;
                }

                Speech.utter(question.question, TextToSpeech.QUEUE_ADD, "question", question.questionLanguage);
            }
            rc = true;
        }
        else
        {
            // finished?
            rc = false;
        }
        return rc;
    }

    private void startTimer(int seconds)
    {
        this.secondsRemaining = seconds;
        updateTimerDisplay(seconds);
        handler.postDelayed(this.runnable, this.second); // update in 1 second
    }

    private void stopTimer() {
        handler.removeCallbacks(this.runnable);
    }

    private void updateTimerDisplay(int seconds)
    {
        TextView countDown = this.getView().findViewById(R.id.textview_countdown);
        if (null == countDown)
            return;

        if (seconds > 0) {
            countDown.setText(Integer.toString(seconds));
        }
        else
        {
            countDown.setText("");
        }
    }

    public void updateRunSeconds(int seconds) {
        TextView tv = this.getView().findViewById(R.id.textview_exercise_seconds);
        if (null != tv)
            tv.setText(Integer.toString(seconds) + " seconds");
    }

    private void setStaticViews(ExerciseContent.ExerciseItem exerciseItem)
    {
        //
        // set static values
        //
        TextView tv = this.getView().findViewById(R.id.textview_title);
        if (null != tv)
            tv.setText(exerciseItem.name );

        tv = this.getView().findViewById(R.id.textview_coming_up);
        if (null != tv) {
            tv.setText(this.questionCount + " of " + exerciseItem.questions.size() + " (#" + (this.currentQuestion + 1) + ")" + "  " + getElapsedTime());
        }

        setStaticName(exerciseItem);
    }

    private void setStaticName() {
        ExercisesActivity activity = (ExercisesActivity) getActivity();
        if (null == activity)
            return;

        setStaticName(activity.getCurrentExercise());
    }

    private void setStaticName(ExerciseContent.ExerciseItem exerciseItem)
    {
        TextView tv = this.getView().findViewById(R.id.textview_exercise_name);
        if (null != tv) {
            ExerciseContent.Question q = exerciseItem.questions.get(this.currentQuestion);
            String text = "";
            if (mAnswerPending == null) {
                // if there's an answer, use it otherwise always show the question
                text = (null != q.answer && q.answer.length() > 0) ? q.answer : q.question;
            }
            else {
                text = q.question;
            }

            tv.setText(text);
        }
    }

    private void updateTimerAudio(int seconds) {
        if (seconds == this.getReadySeconds) {
            Speech.speak("Starting in: ", TextToSpeech.QUEUE_FLUSH);
        }
        else if (seconds <= this.countdownSeconds && seconds > 0) {
            Speech.speak(Integer.toString(seconds), TextToSpeech.QUEUE_FLUSH);
        }
    }

    private void loadFragment(String tag)
    {
        ExercisesActivity activity = (ExercisesActivity) getActivity();
        if (null != activity) {
            activity.loadFragment(tag);
        }
    }
}
