#ifndef WHISPER_H
#define WHISPER_H

#ifdef __cplusplus
extern "C" {
#endif

struct whisper_context;

enum whisper_sampling_strategy {
    WHISPER_SAMPLING_GREEDY,
    WHISPER_SAMPLING_BEAM_SEARCH,
};

struct whisper_full_params {
    enum whisper_sampling_strategy strategy;
    int n_threads;
    int n_max_text_ctx;
    int offset_ms;
    int duration_ms;
    bool translate;
    bool no_context;
    bool single_segment;
    bool print_special;
    bool print_progress;
    bool print_realtime;
    bool print_timestamps;
    const char * language;
    bool detect_language;
};

struct whisper_context * whisper_init_from_file_with_params(const char * path_model, void * params);
struct whisper_full_params whisper_full_default_params(enum whisper_sampling_strategy strategy);
int whisper_full(struct whisper_context * ctx, struct whisper_full_params params, const float * samples, int n_samples);
int whisper_full_parallel(struct whisper_context * ctx, struct whisper_full_params params, const float * samples, int n_samples, int n_processors);
int whisper_full_n_segments(struct whisper_context * ctx);
const char * whisper_full_get_segment_text(struct whisper_context * ctx, int i_segment);
int64_t whisper_full_get_segment_t0(struct whisper_context * ctx, int i_segment);
int64_t whisper_full_get_segment_t1(struct whisper_context * ctx, int i_segment);
void whisper_free(struct whisper_context * ctx);

#ifdef __cplusplus
}
#endif

#endif // WHISPER_H
