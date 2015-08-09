package com.spicymango.fanfictionreader;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;
import android.util.SparseArray;

import com.spicymango.fanfictionreader.activity.LibraryMenuActivity;
import com.spicymango.fanfictionreader.activity.Site;
import com.spicymango.fanfictionreader.provider.SqlConstants;
import com.spicymango.fanfictionreader.provider.StoryProvider;
import com.spicymango.fanfictionreader.util.FileHandler;
import com.spicymango.fanfictionreader.util.Parser;
import com.spicymango.fanfictionreader.util.Result;
import com.spicymango.fanfictionreader.util.Story;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Downloads a story into the library. Must pass the story id inside the intent.
 *
 * @author Michael Chen
 */
public class LibraryDownloader extends IntentService {

    /**
     * Key for the last chapter read.
     */
    private final static String EXTRA_LAST_PAGE = "Last page";

    /**
     * Used for clearing the notification counter
     */
    private final static String EXTRA_UPDATE_NOT = "Notification";

    /**
     * Key for the offset desired
     */
    private final static String EXTRA_OFFSET = "Offset";

    /**
     * Key for the story id
     */
    private final static String EXTRA_STORY_ID = "Story id";

    /**
     * Pattern describing the attributes fields
     */
    private final static Pattern patternAttrib = Pattern.compile(""
            + "(?i)\\ARated: Fiction ([KTM]\\+?) - "//Rating
            + "([^-]+) - "//language
            + "(?:([^ ]+) - )?"//Genre
            + "(?:(?!(?>Chapters))(?:(?! - ).)++ - )?"//characters (non capturing)
            + "(?:Chapters: (\\d++) - )?" //Chapters
            + "Words: ([\\d,]++) - " //Words
            + "(?:Reviews: [\\d,]++ - )?"//Reviews (non capturing)
            + "(?:Favs: ([\\d,]++) - )?"//favorites
            + "(?:Follows: ([\\d,]++))?"); //Follows

    /**
     * Describes the author id Url pattern
     */
    private final static Pattern patternAuthor = Pattern.compile("/u/(\\d++)/");
    /**
     * Used to keep track for notification purposes
     */
    private static int storiesDownloaded = 0;
    /**
     * ID for the notifications generated.
     */
    private int NOTIFICATION_ID = 0;
    private int lastPage;
    private int offset;
    private long storyId;

    public LibraryDownloader() {
        super("Story Downloader");
        setIntentRedelivery(true);
    }

    /**
     * Downloads a story into the library
     *
     * @param context     The current context
     * @param StoryId     The id of the story
     * @param currentPage The current page
     * @param offset      The current offset
     */
    public static void download(Context context, long StoryId, int currentPage, int offset) {
        Intent i = new Intent(context, LibraryDownloader.class);
        i.putExtra(EXTRA_STORY_ID, StoryId);
        i.putExtra(EXTRA_LAST_PAGE, currentPage);
        i.putExtra(EXTRA_OFFSET, offset);
        context.startService(i);
    }

    /**
     * Downloads the story if a newer version is available
     *
     * @return True if the operation succeeds, false otherwise
     */
    private Result download() {
        Story story = new Story();

        int totalPages = 1;
        int incrementalIndex = 0;

        SparseArray<String> array = new SparseArray<String>();
        try {
            for (int currentPage = 1; currentPage <= totalPages; currentPage++) {

                String url = "https://www.fanfiction.net/s/" + storyId + "/"
                        + currentPage + "/";
                Document document = Jsoup.connect(url).timeout(10000).userAgent("Mozilla/5.0").get();

                // Execute on the first run only
                if (totalPages == 1) {
                    //Parse the details
                    story = parseDetails(document);

                    //If an error occurs while parsing, quit
                    if (story == null) {

                        if (document.body().text().contains("Story Not Found")) {
                            return Result.NO_CHANGE;
                        }

                        Log.d(this.getClass().getName(), "Error parsing story attributes");
                        sendReport(document.html());
                        return Result.ERROR_PARSE;
                    }

                    //If no updates have been made to the story, skip
                    if (story.getUpdated().getTime() == lastUpdated()) {
                        ContentResolver resolver = this.getContentResolver();
                        resolver.insert(StoryProvider.FF_CONTENT_URI, story.toContentValues(lastPage, offset));
                        return Result.NO_CHANGE;
                    }

                    totalPages = story.getChapterLenght();

                    //If an update exists and incremental updating is enabled, update chapters as needed
                    if (Settings.isIncrementalUpdatingEnabled(this)) {

                        //If there are more chapters, assume that the story has not been revised
                        int chaptersOnLastUpdate = StoryProvider.numberOfChapters(this, Site.FANFICTION, storyId);
                        if (chaptersOnLastUpdate > 1 && totalPages > chaptersOnLastUpdate) {
                            currentPage = chaptersOnLastUpdate;
                            incrementalIndex = currentPage;
                            continue;
                        }
                    }

                }
                showNotification(story.getName(), currentPage, totalPages);
                String span = document.select("div#storytext").html();

                if (span == null || span.length() == 0) {
                    Log.d(this.getClass().getName(), "Error downloading story text");
                    sendReport(document.html());
                    return Result.ERROR_PARSE;
                }

                array.append(currentPage - 1, span);
            }
        } catch (IOException e) {
            return Result.ERROR_CONNECTION;
        }
        for (int currentPage = incrementalIndex; currentPage < totalPages; currentPage++) {
            if (!FileHandler.writeFile(this, storyId, currentPage + 1, array.get(currentPage)))
                return Result.ERROR_SD;
        }

        ContentResolver resolver = this.getContentResolver();
        resolver.insert(StoryProvider.FF_CONTENT_URI, story.toContentValues(lastPage, offset));

        return Result.SUCCESS;
    }

    /**
     * Writes a log with the latest error
     *
     * @param txt
     */
    private void sendReport(String txt) {
        FileHandler.writeFile(this, 0, 0, txt);
    }

    /**
     * Obtains the last time the story was updated, as a long
     *
     * @return The long corresponding to the date of the last update, or -1
     * if the story is not present in the library.
     */
    private long lastUpdated() {
        ContentResolver resolver = getContentResolver();
        Cursor c = resolver.query(StoryProvider.FF_CONTENT_URI,
                new String[]{SqlConstants.KEY_UPDATED},
                SqlConstants.KEY_STORY_ID + " = ?",
                new String[]{String.valueOf(storyId)}, null);
        if (c == null || !c.moveToFirst()) {
            c.close();
            return -1;
        }
        int index = c.getColumnIndex(SqlConstants.KEY_UPDATED);
        long last = c.getLong(index);
        c.close();
        return last;
    }

    /**
     * Obtains the story object from the one available online
     *
     * @param document The web page
     * @return The story object
     */
    private Story parseDetails(Document document) {

        Elements categoryElements = document.select("div#pre_story_links span a");
        if (categoryElements.isEmpty()) return null;
        String category = categoryElements.last().ownText();

        Element titleElement = document.select("div#profile_top > b").first();
        if (titleElement == null) return null;
        String title = titleElement.ownText();

        Element authorElement = document.select("div#profile_top > a").first();
        if (authorElement == null) return null;
        String author = authorElement.ownText();

        Matcher matcher = patternAuthor.matcher(authorElement.attr("href"));
        if (!matcher.find()) return null;
        int authorId = Integer.valueOf(matcher.group(1));

        Element summaryElement = document.select("div#profile_top > div").first();
        if (summaryElement == null) return null;
        String summary = summaryElement.ownText();

        Element attribs = document.select("div#profile_top > span").last();

        Elements dates = attribs.select("span[data-xutime]");
        if (dates.isEmpty()) return null;
        long updateDate = Long.parseLong(dates.first().attr("data-xutime")) * 1000;
        long publishDate = Long.parseLong(dates.last().attr("data-xutime")) * 1000;


        matcher = patternAttrib.matcher(attribs.text());
        if (!matcher.find()) return null;

        return new Story(storyId, title, author, authorId, summary,
                category, matcher.group(1), matcher.group(2),
                matcher.group(3) == null ? "" : matcher.group(3),
                matcher.group(4) == null ? 1 : Integer.valueOf(matcher.group(4)),
                Parser.parseInt(matcher.group(5)),
                matcher.group(6) == null ? 0 : Parser.parseInt(matcher.group(6)),
                matcher.group(7) == null ? 0 : Parser.parseInt(matcher.group(7)),
                updateDate, publishDate, attribs.text().contains("Complete"));
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent.getBooleanExtra(EXTRA_UPDATE_NOT, false)) {
            storiesDownloaded = 0;
            return;
        }

        storyId = intent.getLongExtra(EXTRA_STORY_ID, -1);
        lastPage = intent.getIntExtra(EXTRA_LAST_PAGE, 1);
        offset = intent.getIntExtra(EXTRA_OFFSET, 0);

        showNotification("", 0, 0);

        Log.d(this.getClass().getName(), "Starting Download");
        switch (download()) {
            case SUCCESS:
                storiesDownloaded++;
                showCompletetionNotification();
                break;
            case ERROR_CONNECTION:
                showErrorNotification(R.string.error_connection);
                break;
            case ERROR_SD:
                showErrorNotification(R.string.error_sd);
                break;
            case ERROR_PARSE:
                showErrorNotification(R.string.error_parsing);
                break;
            case NO_CHANGE:
            default:
                removeNoification();
                break;
        }
        Log.d(this.getClass().getName(), "Ending Download");
    }

    private void showNotification(String storyTitle, int currentPage, int TotalPage) {
        NotificationCompat.Builder notBuilder = new NotificationCompat.Builder(this);
        notBuilder.setContentTitle(getString(R.string.downloader_downloading));

        if (currentPage == 0) {
            notBuilder.setContentText(storyTitle);
        } else {
            notBuilder.setContentText(getString(R.string.downloader_context, storyTitle, currentPage, TotalPage));
        }

        notBuilder.setSmallIcon(android.R.drawable.stat_sys_download);
        notBuilder.setAutoCancel(false);

        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, new Intent(), PendingIntent.FLAG_UPDATE_CURRENT);
        notBuilder.setContentIntent(pendingIntent);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, notBuilder.build());
    }

    private void showErrorNotification(int errorString) {
        NotificationCompat.Builder notBuilder = new NotificationCompat.Builder(this);
        notBuilder.setContentTitle(getString(R.string.downloader_error));
        notBuilder.setContentText(getString(errorString));
        notBuilder.setSmallIcon(R.drawable.ic_action_cancel);
        notBuilder.setAutoCancel(true);

        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, new Intent(), PendingIntent.FLAG_UPDATE_CURRENT);
        notBuilder.setContentIntent(pendingIntent);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, notBuilder.build());
    }

    private void removeNoification() {
        if (storiesDownloaded == 0) {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            manager.cancel(NOTIFICATION_ID);
        } else {
            showCompletetionNotification();
        }
    }

    private void showCompletetionNotification() {
        NotificationCompat.Builder notBuilder = new NotificationCompat.Builder(this);
        String title = getResources().getQuantityString(R.plurals.downloader_notification, storiesDownloaded, storiesDownloaded);
        notBuilder.setContentTitle(title);
        notBuilder.setSmallIcon(R.drawable.ic_action_accept);
        notBuilder.setAutoCancel(true);

        Intent i = new Intent(this, LibraryMenuActivity.class);
        TaskStackBuilder taskBuilder = TaskStackBuilder.create(this);
        taskBuilder.addNextIntentWithParentStack(i);

        Intent e = new Intent(this, LibraryDownloader.class);
        e.putExtra(EXTRA_UPDATE_NOT, true);
        PendingIntent cancelIntent = PendingIntent.getService(this, 0, e, 0);

        PendingIntent pendingIntent = taskBuilder.getPendingIntent(0,
                PendingIntent.FLAG_UPDATE_CURRENT);

        notBuilder.setContentIntent(pendingIntent);
        notBuilder.setDeleteIntent(cancelIntent);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, notBuilder.build());
    }


}
