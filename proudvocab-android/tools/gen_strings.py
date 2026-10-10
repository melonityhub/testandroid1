#!/usr/bin/env python3
"""Generates res/values/strings.xml (en) and res/values-fa/strings.xml (fa) from one table,
so both languages always contain the same keys and the same format placeholders."""
import re, sys, pathlib

S = {
"action_back": ("Back", "بازگشت"),
"action_cancel": ("Cancel", "انصراف"),
"action_close": ("Close", "بستن"),
"action_copy": ("Copy", "کپی"),
"action_delete": ("Delete", "حذف"),
"action_done": ("Done", "تمام"),
"action_edit": ("Edit", "ویرایش"),
"action_remove": ("Remove", "حذف از فهرست"),
"action_save": ("Save", "ذخیره"),

"cd_hard": ("Mark as hard", "علامت‌گذاری به‌عنوان سخت"),
"cd_learned": ("Mark as learned", "علامت‌گذاری به‌عنوان یادگرفته‌شده"),
"cd_more": ("More options", "گزینه‌های بیشتر"),
"cd_next_line": ("Next line", "جملهٔ بعدی"),
"cd_pause": ("Pause", "توقف"),
"cd_play": ("Play", "پخش"),
"cd_prev_line": ("Previous line", "جملهٔ قبلی"),
"cd_replay_line": ("Replay this line", "تکرار این جمله"),
"cd_rotate": ("Rotate screen", "چرخاندن صفحه"),
"cd_speak": ("Pronounce", "تلفظ"),
"cd_toggle_subtitles": ("Show or hide subtitles", "نمایش یا مخفی کردن زیرنویس"),

"edit_context": ("Example sentence", "جملهٔ مثال"),
"edit_translation": ("Translation", "ترجمه"),

"export_done": ("File saved", "فایل ذخیره شد"),
"export_failed": ("Could not save the file", "ذخیرهٔ فایل ناموفق بود"),

"filter_all": ("All", "همه"),
"filter_all_levels": ("All levels", "همهٔ سطح‌ها"),
"filter_hard": ("Hard", "سخت"),
"filter_learned": ("Learned", "یادگرفته"),
"filter_unlearned": ("Not learned", "یادنگرفته"),

"game_almost": ("Almost! It is \"%1$s\"", "تقریباً درسته! درست آن «%1$s» است"),
"game_check": ("Check", "بررسی"),
"game_continue": ("Continue", "ادامه"),
"game_correct": ("Correct!", "درسته!"),
"game_correct_answer": ("Correct answer: %1$s", "پاسخ درست: %1$s"),
"game_dictation_desc": ("Listen and type the word", "بشنو و کلمه را بنویس"),
"game_dictation_hint": ("Type what you hear", "آنچه می‌شنوی را بنویس"),
"game_dictation_question": ("Listen and write the word", "کلمه را بشنو و بنویس"),
"game_dictation_title": ("Dictation", "دیکته"),
"game_fill_desc": ("Pick the missing word in the sentence", "کلمهٔ جاافتاده در جمله را انتخاب کن"),
"game_fill_question": ("Choose the missing word", "کلمهٔ جاافتاده را انتخاب کن"),
"game_fill_title": ("Fill in the blank", "جای خالی"),
"game_match_desc": ("Match words with their translations", "کلمه‌ها را با ترجمه‌شان جفت کن"),
"game_match_question": ("Tap a word, then its translation", "روی یک کلمه و سپس ترجمه‌اش بزن"),
"game_match_title": ("Matching", "جفت‌کردن"),
"game_mc_desc": ("Choose the right translation", "ترجمهٔ درست را انتخاب کن"),
"game_mc_question": ("What does this word mean?", "معنی این کلمه چیست؟"),
"game_mc_title": ("Multiple choice", "چهارگزینه‌ای"),
"game_memory_desc": ("Flip cards and find the pairs", "کارت‌ها را بچرخان و جفت‌ها را پیدا کن"),
"game_memory_question": ("Find each word and its translation", "هر کلمه و ترجمه‌اش را پیدا کن"),
"game_memory_title": ("Memory cards", "کارت حافظه"),
"game_play_again": ("Play again", "بازی دوباره"),
"game_result_exp": ("+%1$d EXP", "+%1$d امتیاز تجربه"),
"game_result_missed": ("Words to review", "کلمه‌هایی که باید مرور کنی"),
"game_result_title": ("Round finished", "دور تمام شد"),
"game_score": ("Score: %1$d", "امتیاز: %1$d"),
"game_scramble_desc": ("Rebuild the word from shuffled letters", "کلمه را از حروف به‌هم‌ریخته بساز"),
"game_scramble_question": ("Arrange the letters to spell the word", "حروف را مرتب کن تا کلمه ساخته شود"),
"game_scramble_title": ("Scrambled letters", "حروف درهم"),
"game_skip": ("Skip", "رد کردن"),
"game_undo": ("Undo", "برگشت"),
"games_include_learned": ("Include words marked as learned", "کلمه‌های «یادگرفته» هم در بازی‌ها باشند"),
"games_need_words": ("Save at least %1$d words to play.", "برای بازی حداقل %1$d کلمه ذخیره کن."),

"import_done": ("Imported %1$d words (%2$d already existed)", "%1$d کلمه وارد شد (%2$d کلمه از قبل بود)"),
"import_failed": ("This file is not a valid backup", "این فایل یک پشتیبان معتبر نیست"),

"library_empty_body": ("Pick a video and its subtitle file to start learning from what you watch.", "یک ویدیو و فایل زیرنویسش را انتخاب کن تا از چیزی که می‌بینی یاد بگیری."),
"library_empty_title": ("No videos yet", "هنوز ویدیویی نیست"),
"library_no_subtitle": ("No subtitle selected", "زیرنویسی انتخاب نشده"),
"library_open": ("Open video & subtitle", "باز کردن ویدیو و زیرنویس"),
"library_pick_video_first": ("Please select a video file too", "لطفاً یک فایل ویدیو هم انتخاب کن"),
"library_recent": ("Recent", "اخیر"),
"library_remove_text": ("Remove \"%1$s\" from the recent list? The file itself is not deleted.", "«%1$s» از فهرست اخیر حذف شود؟ خود فایل پاک نمی‌شود."),
"library_remove_title": ("Remove from list", "حذف از فهرست"),
"library_tagline": ("Watch videos with English subtitles, tap any word to translate and save it.", "ویدیو را با زیرنویس انگلیسی ببین، روی هر کلمه بزن تا ترجمه و ذخیره شود."),
"library_tip": ("Tip: in the file picker, select the video and the subtitle (.srt, .vtt, .ass) together.", "نکته: در انتخاب فایل، ویدیو و زیرنویس (srt، vtt، ass) را با هم انتخاب کن."),

"nav_library": ("Library", "کتابخانه"),
"nav_practice": ("Practice", "تمرین"),
"nav_settings": ("Settings", "تنظیمات"),
"nav_words": ("Words", "لغت‌ها"),

"player_error": ("Playback error (%1$s)", "خطا در پخش (%1$s)"),
"player_error_hint": ("This video format or codec may not be supported on this phone.", "شاید این قالب یا کدک ویدیو روی این گوشی پشتیبانی نشود."),
"player_load_subtitle": ("Choose subtitle file", "انتخاب فایل زیرنویس"),
"player_subtitle_empty": ("No subtitle lines were found in this file.", "در این فایل هیچ زیرنویسی پیدا نشد."),
"player_subtitle_error": ("The subtitle file could not be read.", "خواندن فایل زیرنویس ممکن نشد."),
"player_subtitle_none": ("No subtitle loaded.", "زیرنویسی بارگذاری نشده."),
"player_subtitles_hidden": ("Subtitles hidden", "زیرنویس مخفی است"),
"player_sync": ("Sync", "همگام‌سازی"),
"player_sync_earlier": ("Subtitles earlier (−0.5 s)", "زیرنویس زودتر (−0.5 ثانیه)"),
"player_sync_later": ("Subtitles later (+0.5 s)", "زیرنویس دیرتر (+0.5 ثانیه)"),
"player_sync_reset": ("Reset sync", "بازنشانی همگام‌سازی"),
"player_transcript_empty": ("Load a subtitle file to see the full transcript here.", "برای دیدن متن کامل، یک فایل زیرنویس بارگذاری کن."),

"practice_games": ("Games", "بازی‌ها"),
"practice_review": ("Review", "مرور"),
"practice_stats": ("Stats", "آمار"),

"rate_again": ("Again", "دوباره"),
"rate_easy": ("Easy", "آسان"),
"rate_good": ("Good", "خوب"),
"rate_hard": ("Hard", "سخت"),

"review_all_done": ("You are all caught up. Come back later or save more words.", "همه‌چیز مرور شده. بعداً برگرد یا کلمهٔ بیشتری ذخیره کن."),
"review_done_stats": ("Reviewed %1$d cards, remembered %2$d.", "%1$d کارت مرور شد، %2$d تا را به‌خاطر داشتی."),
"review_done_title": ("Session complete!", "مرور تمام شد!"),
"review_due": ("Due", "موعدرسیده"),
"review_mark_learned": ("I know this word – stop showing it", "این کلمه را بلدم – دیگر نشان نده"),
"review_new": ("New", "جدید"),
"review_no_words_body": ("Tap words while watching a video to save them, then review them here.", "هنگام تماشای ویدیو روی کلمه‌ها بزن تا ذخیره شوند، بعد اینجا مرورشان کن."),
"review_no_words_title": ("No saved words yet", "هنوز کلمه‌ای ذخیره نشده"),
"review_show_answer": ("Show answer", "نمایش پاسخ"),
"review_start": ("Start review", "شروع مرور"),
"review_streak": ("Day streak", "روزهای پیاپی"),
"review_tap_to_flip": ("Tap the card to see the answer", "برای دیدن پاسخ روی کارت بزن"),
"review_today": ("Today", "امروز"),

"settings_about_free": ("All features are free. There is no license or premium.", "همهٔ امکانات رایگان است. هیچ لایسنس یا نسخهٔ ویژه‌ای وجود ندارد."),
"settings_about_version": ("Version %1$s", "نسخهٔ %1$s"),
"settings_auto_pause": ("Pause when tapping a word", "با زدن روی کلمه ویدیو متوقف شود"),
"settings_auto_pause_desc": ("Resumes automatically when you close the word card", "با بستن کارت کلمه دوباره ادامه می‌یابد"),
"settings_auto_speak": ("Pronounce words automatically", "تلفظ خودکار کلمه"),
"settings_cefr_colors": ("Colour words by CEFR level", "رنگی‌کردن کلمه‌ها بر اساس سطح CEFR"),
"settings_cefr_colors_desc": ("A1 green to C2 purple, so hard words stand out", "A1 سبز تا C2 بنفش، تا کلمه‌های سخت دیده شوند"),
"settings_crash_copied": ("Copied", "کپی شد"),
"settings_crash_title": ("Last crash report", "گزارش آخرین خطا"),
"settings_dual": ("Show translation of each line", "نمایش ترجمهٔ هر جمله"),
"settings_dual_desc": ("Needs an internet connection", "نیاز به اینترنت دارد"),
"settings_keep_screen": ("Keep screen on while playing", "روشن ماندن صفحه هنگام پخش"),
"settings_min_level": ("Highlight from level", "برجسته‌کردن از سطح"),
"settings_new_cards": ("New cards per review session: %1$d", "کارت جدید در هر جلسهٔ مرور: %1$d"),
"settings_privacy_text": ("This app only asks for internet access (to translate words). Videos and subtitles are opened with the system file picker, so the app can only see the files you choose. Nothing else on your phone is accessed and your saved words stay on this phone. Words and subtitle lines you ask to translate are sent to Google Translate and a free dictionary service.",
                          "این برنامه فقط دسترسی به اینترنت می‌خواهد (برای ترجمهٔ کلمه‌ها). ویدیو و زیرنویس با انتخابگر فایل خود سیستم باز می‌شوند، پس برنامه فقط فایل‌هایی را می‌بیند که خودت انتخاب می‌کنی. به هیچ چیز دیگری روی گوشی دسترسی ندارد و کلمه‌های ذخیره‌شده فقط روی همین گوشی می‌مانند. کلمه‌ها و جمله‌هایی که ترجمه می‌کنی برای گوگل‌ترنسلیت و یک سرویس دیکشنری رایگان فرستاده می‌شوند."),
"settings_resume": ("Resume where I left off", "ادامه از جایی که ماندم"),
"settings_seconds": ("%1$d s", "%1$d ثانیه"),
"settings_section_about": ("About", "دربارهٔ برنامه"),
"settings_section_appearance": ("Appearance", "ظاهر"),
"settings_section_player": ("Player", "پخش‌کننده"),
"settings_section_privacy": ("Permissions & privacy", "مجوزها و حریم خصوصی"),
"settings_section_study": ("Study", "مطالعه"),
"settings_section_subtitles": ("Subtitles", "زیرنویس"),
"settings_section_translation": ("Translation", "ترجمه"),
"settings_seek_step": ("Skip step", "گام جلو/عقب"),
"settings_show_crash": ("Show last crash report", "نمایش گزارش آخرین خطا"),
"settings_sub_bg": ("Dark background behind subtitles (landscape)", "پس‌زمینهٔ تیره پشت زیرنویس (حالت افقی)"),
"settings_target_lang": ("Translate words into", "ترجمهٔ کلمه‌ها به"),
"settings_text_size": ("Subtitle text size: %1$d", "اندازهٔ متن زیرنویس: %1$d"),
"settings_translation_note": ("Translations come from Google Translate and need internet. Words you saved stay available offline.", "ترجمه‌ها از گوگل‌ترنسلیت می‌آیند و اینترنت می‌خواهند. کلمه‌های ذخیره‌شده بدون اینترنت هم در دسترس‌اند."),

"stats_best_streak": ("Best streak", "بهترین رکورد"),
"stats_by_level": ("Words by CEFR level", "کلمه‌ها بر اساس سطح CEFR"),
"stats_exp": ("%1$d EXP · %2$d games played · %3$d correct answers", "%1$d امتیاز · %2$d بازی · %3$d پاسخ درست"),
"stats_hard": ("Hard", "سخت"),
"stats_learned": ("Learned", "یادگرفته"),
"stats_level": ("Level %1$d", "سطح %1$d"),
"stats_total": ("Saved words", "کلمه‌های ذخیره‌شده"),
"stats_unknown_level": ("Other", "سایر"),

"theme_dark": ("Dark", "تیره"),
"theme_light": ("Light", "روشن"),
"theme_system": ("System", "سیستم"),

"tts_unavailable": ("No text-to-speech voice found. Install \"Speech Services by Google\" and an English voice.", "صدای تبدیل متن به گفتار پیدا نشد. «Speech Services by Google» و یک صدای انگلیسی نصب کن."),

"word_offline_hint": ("Translation unavailable (offline?). You can type your own.", "ترجمه در دسترس نیست (آفلاین؟). می‌توانی خودت بنویسی."),
"word_remove": ("Remove from my words", "حذف از لغت‌هایم"),
"word_removed": ("Word removed", "کلمه حذف شد"),
"word_save": ("Save word", "ذخیرهٔ کلمه"),
"word_saved": ("Word saved", "کلمه ذخیره شد"),
"word_synonyms": ("Synonyms: %1$s", "مترادف‌ها: %1$s"),
"word_translation": ("Translation (%1$s)", "ترجمه (%1$s)"),

"words_backup": ("Back up (JSON)", "پشتیبان‌گیری (JSON)"),
"words_clear_all": ("Delete all words", "حذف همهٔ کلمه‌ها"),
"words_clear_text": ("This will permanently delete all %1$d saved words. Consider making a backup first.", "همهٔ %1$d کلمهٔ ذخیره‌شده برای همیشه حذف می‌شود. بهتر است اول پشتیبان بگیری."),
"words_clear_title": ("Delete all words?", "همهٔ کلمه‌ها حذف شوند؟"),
"words_empty_body": ("Tap any word in a subtitle while watching to save it here.", "هنگام تماشا روی هر کلمهٔ زیرنویس بزن تا اینجا ذخیره شود."),
"words_empty_title": ("Your word list is empty", "فهرست لغت‌هایت خالی است"),
"words_export_anki": ("Export for Anki", "خروجی برای Anki"),
"words_export_csv": ("Export CSV", "خروجی CSV"),
"words_no_match": ("No matching words", "کلمه‌ای پیدا نشد"),
"words_restore": ("Restore from backup", "بازیابی از پشتیبان"),
"words_search": ("Search words", "جستجوی کلمه"),
"words_title": ("My words (%1$d)", "لغت‌های من (%1$d)"),
}

def esc(s):
    s = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    s = s.replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"')
    if s.startswith(("@", "?")): s = "\\" + s
    return s

def write(path, idx, with_app_name):
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
    if with_app_name:
        lines.append('    <string name="app_name" translatable="false">ProudVocab</string>')
    for k in sorted(S):
        lines.append(f'    <string name="{k}">{esc(S[k][idx])}</string>')
    lines.append("</resources>")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")

res = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/res"
write(res / "values/strings.xml", 0, True)
write(res / "values-fa/strings.xml", 1, False)

# --- consistency checks -------------------------------------------------
ph = re.compile(r"%\d\$[sd]")
bad = [k for k, (en, fa) in S.items() if sorted(ph.findall(en)) != sorted(ph.findall(fa))]
if bad:
    print("PLACEHOLDER MISMATCH:", bad); sys.exit(1)
print(f"generated {len(S)} strings x 2 languages; placeholders consistent")
