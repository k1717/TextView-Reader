package com.textview.reader.archive;

/**
 * Centralized first-party RAR3/RAR4 compressed-decoder gap messages.
 *
 * <p>Normal compressed RAR remains libarchive-primary. These messages describe only the
 * fallback first-party unpacker path, so they must not be worded as broad app-level RAR
 * incompatibilities.</p>
 */
final class Rar3FirstPartyGap {
    private Rar3FirstPartyGap() {}

    static RarArchiveReader.UnsupportedRarFeatureException ppmdBlock(boolean keepOldTable) {
        return ppmdModel(keepOldTable);
    }

    static RarArchiveReader.UnsupportedRarFeatureException ppmdModel(boolean keepOldTable) {
        return new RarArchiveReader.UnsupportedRarFeatureException(
                "RAR3/RAR4 PPMd statistical model decoding is not implemented in the "
                        + "first-party unpacker path; pass 30 implements the post-PPMd RAR "
                        + "control layer, pass 31 adds the byte/range-decoder primitive, "
                        + "pass 32 adds suballocator/order-0 model primitives, pass 33 "
                        + "adds context/state/escape-mask/SEE skeletons, and pass 73 adds "
                        + "file-backed PPMd block probing/reporting, but real PPMd "
                        + "context traversal, suffix fallback, masked-symbol arithmetic "
                        + "decoding, SEE table selection, and full model updates still "
                        + "remain first-party gaps. libarchive remains the primary backend "
                        + "for normal compressed RAR (keepOldTable=" + keepOldTable + ")");
    }

    static RarArchiveReader.UnsupportedRarFeatureException ppmdVmFilter(long outputOffset) {
        return new RarArchiveReader.UnsupportedRarFeatureException(
                "RAR3/RAR4 VM filters embedded in PPMd control streams are not implemented in "
                        + "the first-party unpacker path; libarchive remains the primary backend "
                        + "for normal compressed RAR (outputOffset=" + outputOffset + ")");
    }

    static RarArchiveReader.UnsupportedRarFeatureException vmFilter(long outputOffset) {
        return new RarArchiveReader.UnsupportedRarFeatureException(
                "RAR3/RAR4 VM filters are not implemented in the first-party unpacker path; "
                        + "libarchive remains the primary backend for normal compressed RAR "
                        + "(outputOffset=" + outputOffset + ")");
    }

    static RarArchiveReader.UnsupportedRarFeatureException vmFilter(RarVmFilter filter) {
        return new RarArchiveReader.UnsupportedRarFeatureException(
                "RAR3/RAR4 VM filters are not wired into the first-party unpacker output "
                        + "pipeline; pass 34 parses VM-filter metadata, pass 35 adds a "
                        + "standalone E8/E9 standard-filter primitive for isolated byte ranges, "
                        + "pass 36 adds a delayed-output filter queue scaffold, pass 37 "
                        + "adds a buffered filtered-output test harness, pass 38 adds "
                        + "a delayed-buffer memory guard, pass 39 adds an immutable "
                        + "execution-plan validation layer, and pass 40/pass 41 add a "
                        + "decoded-output boundary plus filtered decoded-output adapter "
                        + "test scaffold, but live extractor wiring, custom "
                        + "VM bytecode, Delta/RGB/Audio/Itanium/Upcase "
                        + "semantics, and PPMd-embedded filters remain gaps. libarchive "
                        + "remains the primary backend for normal compressed RAR ("
                        + filter.diagnosticSummary() + ")");
    }

    static RarArchiveReader.UnsupportedRarFeatureException vmFilterMissingExecutionState(
            RarVmFilter filter) {
        return new RarArchiveReader.UnsupportedRarFeatureException(
                "RAR3/RAR4 VM filters are not enabled in the first-party unpacker path; "
                        + "pass 42 adds an execution-state/plan collector boundary, but the "
                        + "live decoder still does not decode the RAR VM register state needed "
                        + "to obtain the filtered output length and file offset. Normal "
                        + "compressed RAR remains libarchive-primary, and first-party VM "
                        + "filters must keep failing cleanly until VM state decoding, output "
                        + "pipeline timing, and filtered CRC validation are proven ("
                        + filter.diagnosticSummary() + ")");
    }
}
