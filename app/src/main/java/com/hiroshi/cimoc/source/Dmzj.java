package com.hiroshi.cimoc.source;

import android.util.Pair;

import com.hiroshi.cimoc.model.Chapter;
import com.hiroshi.cimoc.model.Comic;
import com.hiroshi.cimoc.model.ImageUrl;
import com.hiroshi.cimoc.model.Source;
import com.hiroshi.cimoc.parser.JsonIterator;
import com.hiroshi.cimoc.parser.MangaCategory;
import com.hiroshi.cimoc.parser.MangaParser;
import com.hiroshi.cimoc.parser.SearchIterator;
import com.hiroshi.cimoc.soup.Node;
import com.hiroshi.cimoc.utils.DecryptionUtils;
import com.hiroshi.cimoc.utils.StringUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import okhttp3.Headers;
import okhttp3.Request;

/**
 * Created by Hiroshi on 2016/7/8.
 */
public class Dmzj extends MangaParser {

    public static final int TYPE = 1;
    public static final String DEFAULT_TITLE = "动漫之家";

    public static Source getDefaultSource() {
        return new Source(null, DEFAULT_TITLE, TYPE, true);
    }

    public Dmzj(Source source) {
        init(source, new Category());
    }

    private static final String[] servers = {
            "https://images.dmzj.com/"
    };

    @Override
    public Request getSearchRequest(String keyword, int page) {
        if (page == 1) {
            String url = "http://s.acg.dmzj.com/comicsum/search.php?s=".concat(keyword);
            return new Request.Builder().url(url).build();
        }
        return null;
    }

    @Override
    public SearchIterator getSearchIterator(String html, int page) {
        String jsonString = StringUtils.match("g_search_data = (.*);", html, 1);
        try {
            return new JsonIterator(new JSONArray(jsonString)) {
                @Override
                protected Comic parse(JSONObject object) {
                    try {
                        String cid = object.getString("comic_py");
                        String title = object.getString("name");
                        String cover = "https://" + object.getString("cover").substring(2);
                        //long time = object.getLong("last_updatetime") * 1000;
                        String update = object.getString("last_update_chapter_name");
                        String author = object.optString("authors");
                        return new Comic(TYPE, cid, title, cover, update, author);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return null;
                }
            };
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public Request getInfoRequest(String cid) {
        String url = StringUtils.format("https://www.dmzj.com/info/%s.html", cid);
        return new Request.Builder().url(url).build();
    }

    @Override
    public void parseInfo(String html, Comic comic) {
        try {
//            JSONObject object = new JSONObject(html);
//            String title = object.getString("title");
//            String cover = object.getString("cover");
//            Long time = object.has("last_updatetime") ? object.getLong("last_updatetime") * 1000 : null;
//            String update = time == null ? null : StringUtils.getFormatTime("yyyy-MM-dd", time);
//            String intro = object.optString("description");
//            StringBuilder sb = new StringBuilder();
//            JSONArray array = object.getJSONArray("authors");
//            for (int i = 0; i < array.length(); ++i) {
//                sb.append(array.getJSONObject(i).getString("tag_name")).append(" ");
//            }
//            String author = sb.toString();
//            boolean status = object.getJSONArray("status").getJSONObject(0).getInt("tag_id") == 2310;
            Node body = new Node(html);
            String title = body.text("div.comic_deCon > h1 > a");
            String cover = body.src("div.comic_i_img > a > img");
            String update = body.text("span.list_con_zj");
            String author = body.text("ul.comic_deCon_liO > li:nth-child(1)").substring(3);
            String intro = body.text(" div.comic_deCon > p");
            boolean status = isFinish(body.text(" ul.comic_deCon_liO > li:nth-child(2)").substring(3));
            comic.setInfo(title, cover, update, intro, author, status);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<Chapter> parseChapter(String html) {
        List<Chapter> list = new LinkedList<>();
//        try {
//            JSONObject object = new JSONObject(html);
//            JSONArray array = object.getJSONArray("chapters");
//            for (int i = 0; i != array.length(); ++i) {
//                JSONArray data = array.getJSONObject(i).getJSONArray("data");
//                for (int j = 0; j != data.length(); ++j) {
//                    JSONObject chapter = data.getJSONObject(j);
//                    String title = chapter.getString("chapter_title");
//                    String path = chapter.getString("chapter_id");
//                    list.add(new Chapter(title, path));
//                }
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
        Node body = new Node(html);
        for (Node node : body.list("div.tab-content.tab-content-selected.zj_list_con.autoHeight > ul > li a")) {
            String title = node.text();
            String path = node.hrefWithSplit(2);
            list.add(new Chapter(title, path));
        }
        return list;
    }

    @Override
    public List<Request>  getImagesRequest(String cid, String path) {
        String url = StringUtils.format("https://www.dmzj.com/view/%s/%s.html#@page=1", cid, path);
        List<Request> requests = new ArrayList<>();
        requests.add(new Request.Builder().url(url).build());
        return requests;
    }

    @Override
    public List<ImageUrl> parseImages(String html) {
        List<ImageUrl> list = new LinkedList<>();
        String packed = StringUtils.match("eval(.*?)\\n", html, 1);
        if (packed != null) {
            String result = DecryptionUtils.evalDecrypt(packed);
            packed = StringUtils.match("eval(.*)", result, 1);
            result = DecryptionUtils.evalDecrypt(packed);

            try {
                String jsonString = new JSONObject(StringUtils.match("var pages='(.*)';", result,1)).get("page_url").toString();
                JSONArray array = new JSONArray(jsonString.split("\r\n"));
                int size = array.length();
                for (int i = 0; i != size; ++i) {
                    list.add(new ImageUrl(i + 1, buildUrl(array.getString(i), servers), false));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return list;
    }

    @Override
    public Request getCheckRequest(String cid) {
        return getInfoRequest(cid);
    }

    @Override
    public String parseCheck(String html) {
        try {
            JSONObject object = new JSONObject(html);
            long time = object.getLong("last_updatetime") * 1000;
            return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(time));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public List<Comic> parseCategory(String html, int page) {
        List<Comic> list = new LinkedList<>();
        try {
            JSONArray array = new JSONArray(html);
            for (int i = 0; i != array.length(); ++i) {
                try {
                    JSONObject object = array.getJSONObject(i);
                    if (object.optInt("hidden", 1) != 1) {
                        String cid = object.getString("id");
                        String title = object.getString("name");
                        String cover = "http://images.dmzj.com/".concat(object.getString("cover"));
                        Long time = object.has("last_updatetime") ? object.getLong("last_updatetime") * 1000 : null;
                        String update = time == null ? null : new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(time));
                        String author = object.optString("authors");
                        list.add(new Comic(TYPE, cid, title, cover, update, author));
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return list;
    }

    private static class Category extends MangaCategory {

        @Override
        public boolean isComposite() {
            return true;
        }

        @Override
        public String getFormat(String... args) {
            return StringUtils.format("http://m.dmzj.com/classify/%s-%s-%s-%s-%s-%%d.json",
                    args[CATEGORY_SUBJECT], args[CATEGORY_READER], args[CATEGORY_PROGRESS], args[CATEGORY_AREA], args[CATEGORY_ORDER]);
        }

        @Override
        public List<Pair<String, String>> getSubject() {
            List<Pair<String, String>> list = new ArrayList<>();
            list.add(Pair.create("全部", "0"));
            list.add(Pair.create("冒险", "1"));
            list.add(Pair.create("欢乐向", "2"));
            list.add(Pair.create("格斗", "3"));
            list.add(Pair.create("科幻", "4"));
            list.add(Pair.create("爱情", "5"));
            list.add(Pair.create("竞技", "6"));
            list.add(Pair.create("魔法", "7"));
            list.add(Pair.create("校园", "8"));
            list.add(Pair.create("悬疑", "9"));
            list.add(Pair.create("恐怖", "10"));
            list.add(Pair.create("生活亲情", "11"));
            list.add(Pair.create("百合", "12"));
            list.add(Pair.create("伪娘", "13"));
            list.add(Pair.create("耽美", "14"));
            list.add(Pair.create("后宫", "15"));
            list.add(Pair.create("萌系", "16"));
            list.add(Pair.create("治愈", "17"));
            list.add(Pair.create("武侠", "18"));
            list.add(Pair.create("职场", "19"));
            list.add(Pair.create("奇幻", "20"));
            list.add(Pair.create("节操", "21"));
            list.add(Pair.create("轻小说", "22"));
            list.add(Pair.create("搞笑", "23"));
            return list;
        }

        @Override
        public boolean hasArea() {
            return true;
        }

        @Override
        public List<Pair<String, String>> getArea() {
            List<Pair<String, String>> list = new ArrayList<>();
            list.add(Pair.create("全部", "0"));
            list.add(Pair.create("日本", "1"));
            list.add(Pair.create("内地", "2"));
            list.add(Pair.create("欧美", "3"));
            list.add(Pair.create("港台", "4"));
            list.add(Pair.create("韩国", "5"));
            list.add(Pair.create("其他", "6"));
            return list;
        }

        @Override
        public boolean hasReader() {
            return true;
        }

        @Override
        public List<Pair<String, String>> getReader() {
            List<Pair<String, String>> list = new ArrayList<>();
            list.add(Pair.create("全部", "0"));
            list.add(Pair.create("少年", "1"));
            list.add(Pair.create("少女", "2"));
            list.add(Pair.create("青年", "3"));
            return list;
        }

        @Override
        public boolean hasProgress() {
            return true;
        }

        @Override
        public List<Pair<String, String>> getProgress() {
            List<Pair<String, String>> list = new ArrayList<>();
            list.add(Pair.create("全部", "0"));
            list.add(Pair.create("连载", "1"));
            list.add(Pair.create("完结", "2"));
            return list;
        }

        @Override
        public boolean hasOrder() {
            return true;
        }

        @Override
        public List<Pair<String, String>> getOrder() {
            List<Pair<String, String>> list = new ArrayList<>();
            list.add(Pair.create("更新", "1"));
            list.add(Pair.create("人气", "0"));
            return list;
        }

    }

    @Override
    public Headers getHeader() {
        return Headers.of("Referer", "http://m.dmzj.com/");
    }

}
