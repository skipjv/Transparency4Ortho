package de.melb00m.tr4o.proc;

import de.melb00m.tr4o.helper.CollectionHelper;
import de.melb00m.tr4o.helper.FileHelper;
import de.melb00m.tr4o.helper.LazyAttribute;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.apache.commons.collections4.multimap.UnmodifiableMultiValuedMap;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class OverlayScannerResult {

  private final Map<Path, Path> overlayDsfToSceneryFolderMap;
  private final Map<Path, Path> orthoDsfToSceneryFolderMap;
  private final LazyAttribute<MultiValuedMap<String, Path>> tileToOverlayDsfMap;
  private final LazyAttribute<MultiValuedMap<String, Path>> tileToOrthoDsfMap;
  private final LazyAttribute<SetUtils.SetView<String>> intersectingTiles;
  private final LazyAttribute<Set<Path>> intersectingOverlayDsfs;
  private final LazyAttribute<MultiValuedMap<Path, Path>>
      intersectingOverlayToSceneryDirectoriesMap;

  public OverlayScannerResult(
      final Map<Path, Path> overlayDsfToSceneryFolderMap,
      final Map<Path, Path> orthoDsfToSceneryFolderMap) {
    this.overlayDsfToSceneryFolderMap = Collections.unmodifiableMap(overlayDsfToSceneryFolderMap);
    this.orthoDsfToSceneryFolderMap = Collections.unmodifiableMap(orthoDsfToSceneryFolderMap);
    this.tileToOverlayDsfMap =
        new LazyAttribute<>(() -> extractTileMap(overlayDsfToSceneryFolderMap.keySet()));
    this.tileToOrthoDsfMap =
        new LazyAttribute<>(() -> extractTileMap(this.orthoDsfToSceneryFolderMap.keySet()));
    this.intersectingTiles = new LazyAttribute<>(this::calcIntersectingTiles);
    this.intersectingOverlayDsfs = new LazyAttribute<>(this::calcIntersectingOverlays);
    this.intersectingOverlayToSceneryDirectoriesMap =
        new LazyAttribute<>(this::calcIntersectingOverlayToOrthoDirectories);
  }

  private static MultiValuedMap<String, Path> extractTileMap(final Collection<Path> paths) {
    final var map = new HashSetValuedHashMap<String, Path>();
    paths.forEach(path -> map.put(extractTileNameFromDsfFile(path), path));
    return UnmodifiableMultiValuedMap.unmodifiableMultiValuedMap(map);
  }

  private SetUtils.SetView<String> calcIntersectingTiles() {
    return SetUtils.intersection(
        tileToOverlayDsfMap.get().keySet(), tileToOrthoDsfMap.get().keySet());
  }

  private Set<Path> calcIntersectingOverlays() {
    return CollectionHelper.filteredMapKeys(
            tileToOverlayDsfMap.get().asMap(), key -> getIntersectingTiles().contains(key))
        .values().stream()
        .flatMap(Collection::stream)
        .collect(Collectors.toUnmodifiableSet());
  }

  public MultiValuedMap<Path, Path> calcIntersectingOverlayToOrthoDirectories() {
    final var ovlToOrthoDirsMap = new HashSetValuedHashMap<Path, Path>();
    final var overlayDirToTilesMap = new HashSetValuedHashMap<Path, String>();
    // calc intermediate map that holds the intersecting tile-names contained in an ortho-directory
    CollectionHelper.filteredMapKeys(
            overlayDsfToSceneryFolderMap, key -> getIntersectingOverlayDsfs().contains(key))
        .entrySet()
        .forEach(
            entry ->
                overlayDirToTilesMap.put(
                    entry.getValue(), extractTileNameFromDsfFile(entry.getKey())));
    // retrieve the corresponding ortho-dsfs and their directories for each of the overlay
    // directories
    overlayDirToTilesMap
        .asMap()
        .entrySet()
        .forEach(
            entry ->
                entry.getValue().stream()
                    .flatMap(tile -> tileToOrthoDsfMap.get().get(tile).stream())
                    .map(orthoDsfToSceneryFolderMap::get)
                    .forEach(orthoDir -> ovlToOrthoDirsMap.put(entry.getKey(), orthoDir)));

    return UnmodifiableMultiValuedMap.unmodifiableMultiValuedMap(ovlToOrthoDirsMap);
  }

  private static String extractTileNameFromDsfFile(final Path path) {
    return FileHelper.removeFileExtension(path.getFileName().toString());
  }

  public Set<String> getIntersectingTiles() {
    return intersectingTiles.get();
  }

  public Set<Path> getIntersectingOverlayDsfs() {
    return intersectingOverlayDsfs.get();
  }

  public MultiValuedMap<Path, Path> getIntersectingOverlayToSceneryDirectoriesMap() {
    return intersectingOverlayToSceneryDirectoriesMap.get();
  }
}