package com.teamarcadia.arcadiatweaks.common.modules.sparework;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Work that other mods and the game repeat for nothing, skipped with the same results.
 *
 * @author vyrriox
 */
public final class SpareWorkConfig {

    public final ModConfigSpec.BooleanValue fastSuiteSkipEmptyParallelLookup;
    public final ModConfigSpec.BooleanValue simplySwordsReusePityStack;
    public final ModConfigSpec.BooleanValue structureLookupUsesLoadedChunk;
    public final ModConfigSpec.BooleanValue crabbersDelightSkipEmptyTackleBoxSearch;

    public SpareWorkConfig(ModConfigSpec.Builder b) {
        b.comment(
                "Work other mods and the game repeat for nothing. Every entry gives the same results and the same",
                "timing as without it. Dedicated server only. Each entry is a kill-switch, applied from the next tick."
        ).push("sparework");

        fastSuiteSkipEmptyParallelLookup = b
                .comment(
                        "FastSuite: a recipe type with no parallel recipe (every Create type, for example) still sent",
                        "each lookup to the thread pool, to stream an empty list, and waited for the answer.",
                        "The answer is known: nothing. The lookup now goes straight to the serial recipes.",
                        "Turns itself off, with a warning, on a FastSuite build other than the verified one."
                )
                .define("fastsuite_skip_empty_parallel_lookup", true);

        simplySwordsReusePityStack = b
                .comment(
                        "Simply Swords: every read of a chest slot created and threw away a stack for the pity loot,",
                        "and a thread local entry to hold it. The stack is kept on the server thread instead.",
                        "Turns itself off, with a warning, on a Simply Swords build other than the verified one."
                )
                .define("simplyswords_reuse_pity_stack", true);

        structureLookupUsesLoadedChunk = b
                .comment(
                        "Structure lookups (Twilight Forest asks for every natural spawn, in every dimension) asked",
                        "the chunk system for an older status of the chunk, which missed its cache and added a ticket.",
                        "A chunk that is already fully loaded holds the same structure data and is now read directly.",
                        "Any other chunk, and any other thread, still goes through the original call."
                )
                .define("structure_lookup_uses_loaded_chunk", true);

        crabbersDelightSkipEmptyTackleBoxSearch = b
                .comment(
                        "Crabber's Delight: every player tick reads the 343 positions around the player to find a",
                        "placed tackle box. The search is skipped when the loaded chunks prove it finds nothing and",
                        "changes nothing: no tackle box, no pending, removed or missing block entity. Anything else",
                        "runs the original search. Turns itself off, with a warning, on another Crabber's Delight build."
                )
                .define("crabbersdelight_skip_empty_tackle_box_search", true);

        b.pop();
    }
}
