package com.udacity.popularmovies.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.udacity.popularmovies.DiscoveryMode;
import com.udacity.popularmovies.settings.AppPreferences;
import com.udacity.popularmovies.settings.DiscoveryCache;
import com.udacity.popularmovies.themoviedb.api.MovieApi;
import com.udacity.popularmovies.themoviedb.api.data.MovieInfo;

import java.util.concurrent.TimeUnit;

import androidx.lifecycle.Observer;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

/**
 * Tasks for the sync of the cached data for the movie discovery
 * Also handles weather to load data from api or from the db (favourites)
 */
public class SyncDiscoveryTask
{

    // TODO: 06.04.2020 set to real time
    private static final int TIME_INTERVAL_MIN_HOURS = 1;
    private static final int TIME_INTERVAL_MAX_HOURS = 2;

    private static final String SCHEDULED_SYNC_TASK_TAG = "scheduled-cache-sync";
    private static boolean sInitialized;

    public static synchronized void initialize(Context context)
    {
        if(sInitialized) return;

        sInitialized = true;

        scheduleReoccurringUpdates(context);
    }


    public static synchronized void scheduleReoccurringUpdates(Context context)
    {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                UpdateCacheWorker.class,
                TIME_INTERVAL_MIN_HOURS, TimeUnit.HOURS,
                TIME_INTERVAL_MAX_HOURS,TimeUnit.HOURS)
                .setConstraints(constraints)
                .addTag(SCHEDULED_SYNC_TASK_TAG)
                .build();

        WorkManager.getInstance(context).enqueue(workRequest);

        WorkManager.getInstance(context).getWorkInfoByIdLiveData(workRequest.getId()).observe(context, new Observer<WorkInfo>(){
            @Override
            public void onChanged(WorkInfo workInfo) {
                Log.d(SCHEDULED_SYNC_TASK_TAG,"Worker state changed: " + workInfo.getState().toString());
            }
        });
    }

    public static synchronized void syncCachedDiscoveryData(Context context)
    {
        DiscoveryMode mode = AppPreferences.getLatestDiscoveryMode(context);
        MovieApi api = new MovieApi();

        if(mode == DiscoveryMode.FAVOURITES)
        {
            // todo use database to pull the data
        }
        else
        {
            MovieInfo[] popularMovies = api.getMoviesByPopularity();
            MovieInfo[] bestRatedMovies = api.getMoviesByRating();

            // TODO: 06.04.2020 fix this when api returns strings
            DiscoveryCache cache = new DiscoveryCache(
                    new Gson().toJson(popularMovies),
                    new Gson().toJson(bestRatedMovies)
            );
            AppPreferences.updateDiscoveryCache(context,cache);
        }
    }

}
