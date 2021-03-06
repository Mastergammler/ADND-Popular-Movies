package com.udacity.popularmovies.ui.moviedetails;

import android.content.Context;
import android.database.sqlite.SQLiteConstraintException;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.squareup.picasso.Picasso;
import com.udacity.popularmovies.favouritesdb.AppExecutors;
import com.udacity.popularmovies.favouritesdb.Entitites.MovieCover;
import com.udacity.popularmovies.favouritesdb.Entitites.MovieData;
import com.udacity.popularmovies.favouritesdb.Entitites.ReviewData;
import com.udacity.popularmovies.favouritesdb.Entitites.VideoData;
import com.udacity.popularmovies.favouritesdb.FavouritesDatabase;
import com.udacity.popularmovies.themoviedb.IMovieDbApi;
import com.udacity.popularmovies.themoviedb.api.MovieApi;
import com.udacity.popularmovies.themoviedb.api.data.ImageSize;
import com.udacity.popularmovies.themoviedb.api.data.MovieInfo;
import com.udacity.popularmovies.themoviedb.api.data.MovieReview;
import com.udacity.popularmovies.themoviedb.api.data.VideoInfo;

import java.io.IOException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.AsyncTaskLoader;
import androidx.loader.content.Loader;

public class MovieDetailLoaderCallback implements LoaderManager.LoaderCallbacks<MovieDetails>
{
    public static final String LOADER_PARAM = "loader-param-movie-id";
    public static final String LOADER_PARAM_SAVE_OR_DELETE = "loader-save-to-db";

    private Context mContext;
    private IMovieDbApi mMovieApi;
    private TextView mDebugTextView;

    public MovieDetailLoaderCallback(Context context, TextView debugTextView)
    {
        mContext = context;
        mMovieApi = new MovieApi();
        mDebugTextView = debugTextView;
    }

    @NonNull
    @Override
    public Loader<MovieDetails> onCreateLoader(int id, @Nullable final Bundle args)
    {
        return new AsyncTaskLoader<MovieDetails>(mContext)
        {
            private MovieDetails movieDetailsCache;

            @Override
            protected void onStartLoading()
            {
                super.onStartLoading();

                if(movieDetailsCache != null)
                {
                    deliverResult(movieDetailsCache);
                }
                else
                {
                    // TODO: 07.04.2020 show loading indicator
                    forceLoad();
                }
            }

            @Nullable
            @Override
            public MovieDetails loadInBackground()
            {
                if(argumentsAreInvalid()) return null;

                int id = args.getInt(LOADER_PARAM);

                return loadMovieDetails(id);
            }
            private boolean argumentsAreInvalid()
            {
                if(args == null)
                {
                    Log.w(this.getClass().getSimpleName(),"No parameter specified for the loader!");
                    return true;
                }
                if(args.getInt(LOADER_PARAM) == 0)
                {
                    Log.w(this.getClass().getSimpleName(),"Invalid movie id specified! Unable to load movie details!");
                    return true;
                }
                return false;
            }
            private MovieDetails loadMovieDetails(int movieId)
            {
                MovieInfo info = mMovieApi.getMovieDetails(movieId);
                VideoInfo[] trailerUrls = mMovieApi.getVideoLinks(movieId,true);
                MovieReview[] reviews = mMovieApi.getMovieReviews(movieId);

                return new MovieDetails(info,reviews,trailerUrls);
            }

            @Override
            public void deliverResult(@Nullable MovieDetails data)
            {
                if(data == null)
                {
                    Log.w("Loader callback","Movie data is null, data not loaded");
                    return;
                }
                movieDetailsCache = data;

                if(args != null && args.containsKey(LOADER_PARAM_SAVE_OR_DELETE))
                {
                    boolean save = args.getBoolean(LOADER_PARAM_SAVE_OR_DELETE);

                    if(save) saveResultToDb(data);
                    else deleteResultFromDb(data);
                }
                super.deliverResult(data);
            }
            private void deleteResultFromDb(final MovieDetails data)
            {
                AppExecutors.getInstance().runOnDiskIOThread(
                        new Runnable(){
                            @Override
                            public void run() {
                                int movieId = data.movieInfo.id;
                                FavouritesDatabase db = FavouritesDatabase.getInstance(mContext);
                                int amount = db.favouritesDao().deleteCover(movieId);
                                int amount2 = db.favouritesDao().deleteVideos(movieId);
                                int amount3 = db.favouritesDao().deleteMovieFromFavourites(movieId);
                                int amount4 = db.favouritesDao().deleteReviews(movieId);
                            }
                        }
                );
            }
            private void saveResultToDb(final MovieDetails data)
            {
                AppExecutors.getInstance().runOnDiskIOThread(
                        new Runnable(){
                            @Override
                            public void run()
                            {
                                Bitmap bitmap = loadCoverBitmap(data.movieInfo.poster_path);
                                int movieId = data.movieInfo.id;

                                ReviewData[] reviews = new ReviewData[data.movieReviews.length];
                                VideoData[] videos = new VideoData[data.movieTrailers.length];
                                MovieData movieData = new MovieData(data.movieInfo);
                                MovieCover cover = new MovieCover(movieId,bitmap);

                                for(int i = 0; i < data.movieReviews.length; i++)
                                {
                                    reviews[i] = new ReviewData(movieId,data.movieReviews[i]);
                                }
                                for(int i = 0; i < data.movieTrailers.length; i++)
                                {
                                    videos[i] = new VideoData(movieId,data.movieTrailers[i]);
                                }

                                saveToDb(reviews,videos,movieData,cover);
                            }
                        }
                );
            }
            private Bitmap loadCoverBitmap(String posterPath)
            {
                Uri uri = mMovieApi.getMoviePoster(posterPath, ImageSize.IMAGE_MEDIUM);
                Bitmap bitmap = null;

                try
                {
                    bitmap = Picasso.get().load(uri).get();
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }

                return bitmap;
            }
            private void saveToDb(ReviewData[] reviews, VideoData[] videos, MovieData movieData, MovieCover coverData)
            {
                FavouritesDatabase db = FavouritesDatabase.getInstance(mContext);
                try
                {
                    db.favouritesDao().saveReviews(reviews);
                    db.favouritesDao().saveVideos(videos);
                    db.favouritesDao().saveMovieAsFavourite(movieData);
                    db.favouritesDao().saveCover(coverData);
                }
                catch (SQLiteConstraintException e)
                {
                    // Happens if for some reason it's tried to re-save the same object
                    // That means that the object is already in the db, so there is nothing to do
                    e.printStackTrace();
                }
            }
        };
    }

    @Override
    public void onLoadFinished(@NonNull Loader<MovieDetails> loader, MovieDetails data)
    {
        if(data != null)
        {
            TextView tv = mDebugTextView;
            if(data.movieInfo != null)
            {

                tv.setText(data.movieInfo.tagline != null ? data.movieInfo.tagline : "");
                tv.append("\n" + data.movieInfo.status);
                tv.append("\n" + data.movieInfo.runtime);
                tv.append("\n" + data.movieInfo.revenue + "$$$$$");
            }

            if(data.movieReviews != null)
            {
                for(VideoInfo uri : data.movieTrailers)
                {
                    tv.append("\n" + uri.buildVideoUrl().toString());
                }
            }
            if(data.movieTrailers != null)
            {
                for(MovieReview rev : data.movieReviews)
                {
                    tv.append("\n" + rev.author + " : " + rev.getContentPreview());
                }
            }
        }
    }

    @Override
    public void onLoaderReset(@NonNull Loader<MovieDetails> loader) {}
}
