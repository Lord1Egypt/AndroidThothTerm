/*
 * Extracts ARCHIVE under PREFIX the way libalpm does: absolute entry names and
 * ARCHIVE_EXTRACT_PERM, printing pacman's own warning text for ARCHIVE_WARN.
 * Usage: alpm-extract ARCHIVE PREFIX
 */
#include <archive.h>
#include <archive_entry.h>
#include <stdio.h>
int main(int argc, char **argv) {
    struct archive *a = archive_read_new(), *w = archive_write_disk_new();
    struct archive_entry *e;
    (void) argc;
    archive_read_support_format_all(a);
    archive_read_support_filter_all(a);
    archive_write_disk_set_options(w, ARCHIVE_EXTRACT_TIME | ARCHIVE_EXTRACT_PERM
        | ARCHIVE_EXTRACT_ACL | ARCHIVE_EXTRACT_FFLAGS | ARCHIVE_EXTRACT_XATTR | ARCHIVE_EXTRACT_UNLINK
        | ARCHIVE_EXTRACT_SECURE_SYMLINKS);
    if (archive_read_open_filename(a, argv[1], 10240)) return 1;
    while (archive_read_next_header(a, &e) == ARCHIVE_OK) {
        char path[4096];
        snprintf(path, sizeof path, "%s/%s", argv[2], archive_entry_pathname(e));
        archive_entry_set_pathname(e, path);
        int r = archive_write_header(w, e);
        if (r == ARCHIVE_OK && archive_entry_size(e) > 0) {
            const void *buf; size_t size; la_int64_t off;
            while (archive_read_data_block(a, &buf, &size, &off) == ARCHIVE_OK)
                archive_write_data_block(w, buf, size, off);
        }
        r = archive_write_finish_entry(w);
        if (r == ARCHIVE_WARN)
            printf("warning: warning given when extracting %s (%s)\n", path, archive_error_string(w));
        else if (r != ARCHIVE_OK)
            printf("error: %s (%s)\n", path, archive_error_string(w));
    }
    return 0;
}
