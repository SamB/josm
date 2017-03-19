// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.actions;

import static org.openstreetmap.josm.tools.I18n.marktr;
import static org.openstreetmap.josm.tools.I18n.tr;
import static org.openstreetmap.josm.tools.I18n.trn;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.JOptionPane;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.ReverseWayAction.ReverseWayResult;
import org.openstreetmap.josm.actions.SplitWayAction.SplitWayResult;
import org.openstreetmap.josm.command.AddCommand;
import org.openstreetmap.josm.command.ChangeCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.DeleteCommand;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.NodePositionComparator;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.TagCollection;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.gui.conflict.tags.CombinePrimitiveResolverDialog;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.tools.Geometry;
import org.openstreetmap.josm.tools.JosmRuntimeException;
import org.openstreetmap.josm.tools.Pair;
import org.openstreetmap.josm.tools.Shortcut;
import org.openstreetmap.josm.tools.UserCancelException;
import org.openstreetmap.josm.tools.Utils;
import org.openstreetmap.josm.tools.bugreport.BugReport;

/**
 * Join Areas (i.e. closed ways and multipolygons).
 * @since 2575
 */
public class JoinAreasAction extends JosmAction {
    // This will be used to commit commands and unite them into one large command sequence at the end
    private final transient LinkedList<Command> cmds = new LinkedList<>();
    private int cmdsCount;
    private final transient List<Relation> addedRelations = new LinkedList<>();

    /**
     * This helper class describes join areas action result.
     * @author viesturs
     */
    public static class JoinAreasResult {

        private final boolean hasChanges;
        private final List<Multipolygon> polygons;

        /**
         * Constructs a new {@code JoinAreasResult}.
         * @param hasChanges whether the result has changes
         * @param polygons the result polygons, can be null
         */
        public JoinAreasResult(boolean hasChanges, List<Multipolygon> polygons) {
            this.hasChanges = hasChanges;
            this.polygons = polygons;
        }

        /**
         * Determines if the result has changes.
         * @return {@code true} if the result has changes
         */
        public final boolean hasChanges() {
            return hasChanges;
        }

        /**
         * Returns the result polygons, can be null.
         * @return the result polygons, can be null
         */
        public final List<Multipolygon> getPolygons() {
            return polygons;
        }
    }

    public static class Multipolygon {
        private final Way outerWay;
        private final List<Way> innerWays;

        /**
         * Constructs a new {@code Multipolygon}.
         * @param way outer way
         */
        public Multipolygon(Way way) {
            outerWay = way;
            innerWays = new ArrayList<>();
        }

        /**
         * Returns the outer way.
         * @return the outer way
         */
        public final Way getOuterWay() {
            return outerWay;
        }

        /**
         * Returns the inner ways.
         * @return the inner ways
         */
        public final List<Way> getInnerWays() {
            return innerWays;
        }
    }

    // HelperClass
    // Saves a relation and a role an OsmPrimitve was part of until it was stripped from all relations
    private static class RelationRole {
        public final Relation rel;
        public final String role;

        RelationRole(Relation rel, String role) {
            this.rel = rel;
            this.role = role;
        }

        @Override
        public int hashCode() {
            return Objects.hash(rel, role);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            RelationRole that = (RelationRole) other;
            return Objects.equals(rel, that.rel) &&
                    Objects.equals(role, that.role);
        }
    }

    /**
     * HelperClass - saves a way and the "inside" side.
     *
     * insideToTheLeft: if true left side is "in", false -right side is "in".
     * Left and right are determined along the orientation of way.
     */
    public static class WayInPolygon {
        public final Way way;
        public boolean insideToTheRight;

        public WayInPolygon(Way way, boolean insideRight) {
            this.way = way;
            this.insideToTheRight = insideRight;
        }

        @Override
        public int hashCode() {
            return Objects.hash(way, insideToTheRight);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            WayInPolygon that = (WayInPolygon) other;
            return insideToTheRight == that.insideToTheRight &&
                    Objects.equals(way, that.way);
        }

        @Override
        public String toString() {
            return "WayInPolygon [way=" + way + ", insideToTheRight=" + insideToTheRight + "]";
        }
    }

    /**
     * This helper class describes a polygon, assembled from several ways.
     * @author viesturs
     *
     */
    public static class AssembledPolygon {
        public List<WayInPolygon> ways;

        public AssembledPolygon(List<WayInPolygon> boundary) {
            this.ways = boundary;
        }

        public List<Node> getNodes() {
            List<Node> nodes = new ArrayList<>();
            for (WayInPolygon way : this.ways) {
                //do not add the last node as it will be repeated in the next way
                if (way.insideToTheRight) {
                    for (int pos = 0; pos < way.way.getNodesCount() - 1; pos++) {
                        nodes.add(way.way.getNode(pos));
                    }
                } else {
                    for (int pos = way.way.getNodesCount() - 1; pos > 0; pos--) {
                        nodes.add(way.way.getNode(pos));
                    }
                }
            }

            return nodes;
        }

        /**
         * Inverse inside and outside
         */
        public void reverse() {
            for (WayInPolygon way: ways) {
                way.insideToTheRight = !way.insideToTheRight;
            }
            Collections.reverse(ways);
        }
    }

    public static class AssembledMultipolygon {
        public AssembledPolygon outerWay;
        public List<AssembledPolygon> innerWays;

        public AssembledMultipolygon(AssembledPolygon way) {
            outerWay = way;
            innerWays = new ArrayList<>();
        }
    }

    /**
     * This hepler class implements algorithm traversing trough connected ways.
     * Assumes you are going in clockwise orientation.
     * @author viesturs
     */
    private static class WayTraverser {

        /** Set of {@link WayInPolygon} to be joined by walk algorithm */
        private final List<WayInPolygon> availableWays;
        /** Current state of walk algorithm */
        private WayInPolygon lastWay;
        /** Direction of current way */
        private boolean lastWayReverse;

        /** Constructor
         * @param ways available ways
         */
        WayTraverser(Collection<WayInPolygon> ways) {
            availableWays = new ArrayList<>(ways);
            lastWay = null;
        }

        /**
         *  Remove ways from available ways
         *  @param ways Collection of WayInPolygon
         */
        public void removeWays(Collection<WayInPolygon> ways) {
            availableWays.removeAll(ways);
        }

        /**
         * Remove a single way from available ways
         * @param way WayInPolygon
         */
        public void removeWay(WayInPolygon way) {
            availableWays.remove(way);
        }

        /**
         * Reset walk algorithm to a new start point
         * @param way New start point
         */
        public void setStartWay(WayInPolygon way) {
            lastWay = way;
            lastWayReverse = !way.insideToTheRight;
        }

        /**
         * Reset walk algorithm to a new start point.
         * @return The new start point or null if no available way remains
         */
        public WayInPolygon startNewWay() {
            if (availableWays.isEmpty()) {
                lastWay = null;
            } else {
                lastWay = availableWays.iterator().next();
                lastWayReverse = !lastWay.insideToTheRight;
            }

            return lastWay;
        }

        /**
         * Walking through {@link WayInPolygon} segments, head node is the current position
         * @return Head node
         */
        private Node getHeadNode() {
            return !lastWayReverse ? lastWay.way.lastNode() : lastWay.way.firstNode();
        }

        /**
         * Node just before head node.
         * @return Previous node
         */
        private Node getPrevNode() {
            return !lastWayReverse ? lastWay.way.getNode(lastWay.way.getNodesCount() - 2) : lastWay.way.getNode(1);
        }

        /**
         * Returns oriented angle (N1N2, N1N3) in range [0; 2*Math.PI[
         * @param n1 first node
         * @param n2 second node
         * @param n3 third node
         * @return oriented angle (N1N2, N1N3) in range [0; 2*Math.PI[
         */
        private static double getAngle(Node n1, Node n2, Node n3) {
            EastNorth en1 = n1.getEastNorth();
            EastNorth en2 = n2.getEastNorth();
            EastNorth en3 = n3.getEastNorth();
            double angle = Math.atan2(en3.getY() - en1.getY(), en3.getX() - en1.getX()) -
                    Math.atan2(en2.getY() - en1.getY(), en2.getX() - en1.getX());
            while (angle >= 2*Math.PI) {
                angle -= 2*Math.PI;
            }
            while (angle < 0) {
                angle += 2*Math.PI;
            }
            return angle;
        }

        /**
         * Get the next way creating a clockwise path, ensure it is the most right way. #7959
         * @return The next way.
         */
        public WayInPolygon walk() {
            Node headNode = getHeadNode();
            Node prevNode = getPrevNode();

            double headAngle = Math.atan2(headNode.getEastNorth().east() - prevNode.getEastNorth().east(),
                    headNode.getEastNorth().north() - prevNode.getEastNorth().north());

            // Pairs of (way, nextNode)
            lastWay = Stream.concat(
                availableWays.stream()
                    .filter(way -> way.way.firstNode().equals(headNode) && way.insideToTheRight)
                    .map(way -> new Pair<>(way, way.way.getNode(1))),
                availableWays.stream()
                    .filter(way -> way.way.lastNode().equals(headNode) && !way.insideToTheRight)
                    .map(way -> new Pair<>(way, way.way.getNode(way.way.getNodesCount() - 2))))

                // now find the way with the best angle
                .min(Comparator.comparingDouble(wayAndNext -> {
                    Node nextNode = wayAndNext.b;
                    if (nextNode == prevNode) {
                        // we always prefer going back.
                        return Double.POSITIVE_INFINITY;
                    }
                    double angle = Math.atan2(nextNode.getEastNorth().east() - headNode.getEastNorth().east(),
                            nextNode.getEastNorth().north() - headNode.getEastNorth().north()) - headAngle;
                    if (angle > Math.PI)
                        angle -= 2*Math.PI;
                    if (angle <= -Math.PI)
                        angle += 2*Math.PI;
                    return angle;
                })).map(wayAndNext -> wayAndNext.a).orElse(null);
            lastWayReverse = lastWay != null && !lastWay.insideToTheRight;
            return lastWay;
        }

        /**
         * Search for an other way coming to the same head node at left side from last way. #9951
         * @return left way or null if none found
         */
        public WayInPolygon leftComingWay() {
            Node headNode = getHeadNode();
            Node prevNode = getPrevNode();

            WayInPolygon mostLeft = null; // most left way connected to head node
            boolean comingToHead = false; // true if candidate come to head node
            double angle = 2*Math.PI;

            for (WayInPolygon candidateWay : availableWays) {
                boolean candidateComingToHead;
                Node candidatePrevNode;

                if (candidateWay.way.firstNode().equals(headNode)) {
                    candidateComingToHead = !candidateWay.insideToTheRight;
                    candidatePrevNode = candidateWay.way.getNode(1);
                } else if (candidateWay.way.lastNode().equals(headNode)) {
                     candidateComingToHead = candidateWay.insideToTheRight;
                     candidatePrevNode = candidateWay.way.getNode(candidateWay.way.getNodesCount() - 2);
                } else
                    continue;
                if (candidateComingToHead && candidateWay.equals(lastWay))
                    continue;

                double candidateAngle = getAngle(headNode, candidatePrevNode, prevNode);

                if (mostLeft == null || candidateAngle < angle || (Utils.equalsEpsilon(candidateAngle, angle) && !candidateComingToHead)) {
                    // Candidate is most left
                    mostLeft = candidateWay;
                    comingToHead = candidateComingToHead;
                    angle = candidateAngle;
                }
            }

            return comingToHead ? mostLeft : null;
        }

        @Override
        public String toString() {
            return "WayTraverser [availableWays=" + availableWays + ", lastWay=" + lastWay + ", lastWayReverse="
                    + lastWayReverse + "]";
        }
    }

    /**
     * Helper storage class for finding findOuterWays
     * @author viesturs
     */
    static class PolygonLevel {
        public final int level;
        public final AssembledMultipolygon pol;

        PolygonLevel(AssembledMultipolygon pol, int level) {
            this.pol = pol;
            this.level = level;
        }
    }

    /**
     * Constructs a new {@code JoinAreasAction}.
     */
    public JoinAreasAction() {
        this(true);
    }

    /**
     * Constructs a new {@code JoinAreasAction} with optional shortcut.
     * @param addShortcut controls whether the shortcut should be registered or not
     * @since 11611
     */
    public JoinAreasAction(boolean addShortcut) {
        super(tr("Join overlapping Areas"), "joinareas", tr("Joins areas that overlap each other"), addShortcut ?
        Shortcut.registerShortcut("tools:joinareas", tr("Tool: {0}", tr("Join overlapping Areas")), KeyEvent.VK_J, Shortcut.SHIFT)
        : null, true);
    }

    /**
     * Gets called whenever the shortcut is pressed or the menu entry is selected.
     * Checks whether the selected objects are suitable to join and joins them if so.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        join(Main.getLayerManager().getEditDataSet().getSelectedWays());
    }

    /**
     * Joins the given ways.
     * @param ways Ways to join
     * @since 7534
     */
    public void join(Collection<Way> ways) {
        addedRelations.clear();

        if (ways.isEmpty()) {
            new Notification(
                    tr("Please select at least one closed way that should be joined."))
                    .setIcon(JOptionPane.INFORMATION_MESSAGE)
                    .show();
            return;
        }

        List<Node> allNodes = new ArrayList<>();
        for (Way way : ways) {
            if (!way.isClosed()) {
                new Notification(
                        tr("One of the selected ways is not closed and therefore cannot be joined."))
                        .setIcon(JOptionPane.INFORMATION_MESSAGE)
                        .show();
                return;
            }

            allNodes.addAll(way.getNodes());
        }

        // TODO: Only display this warning when nodes outside dataSourceArea are deleted
        boolean ok = Command.checkAndConfirmOutlyingOperation("joinarea", tr("Join area confirmation"),
                trn("The selected way has nodes outside of the downloaded data region.",
                    "The selected ways have nodes outside of the downloaded data region.",
                    ways.size()) + "<br/>"
                    + tr("This can lead to nodes being deleted accidentally.") + "<br/>"
                    + tr("Are you really sure to continue?")
                    + tr("Please abort if you are not sure"),
                tr("The selected area is incomplete. Continue?"),
                allNodes, null);
        if (!ok) return;

        //analyze multipolygon relations and collect all areas
        List<Multipolygon> areas = collectMultipolygons(ways);

        if (areas == null)
            //too complex multipolygon relations found
            return;

        if (!testJoin(areas)) {
            new Notification(
                    tr("No intersection found. Nothing was changed."))
                    .setIcon(JOptionPane.INFORMATION_MESSAGE)
                    .show();
            return;
        }

        if (!resolveTagConflicts(areas))
            return;
        //user canceled, do nothing.

        try {
            // see #11026 - Because <ways> is a dynamic filtered (on ways) of a filtered (on selected objects) collection,
            // retrieve effective dataset before joining the ways (which affects the selection, thus, the <ways> collection)
            // Dataset retrieving allows to call this code without relying on Main.getCurrentDataSet(), thus, on a mapview instance
            DataSet ds = ways.iterator().next().getDataSet();

            // Do the job of joining areas
            JoinAreasResult result = joinAreas(areas);

            if (result.hasChanges) {
                // move tags from ways to newly created relations
                // TODO: do we need to also move tags for the modified relations?
                for (Relation r: addedRelations) {
                    cmds.addAll(CreateMultipolygonAction.removeTagsFromWaysIfNeeded(r));
                }
                commitCommands(tr("Move tags from ways to relations"));

                List<Way> allWays = new ArrayList<>();
                for (Multipolygon pol : result.polygons) {
                    allWays.add(pol.outerWay);
                    allWays.addAll(pol.innerWays);
                }
                if (ds != null) {
                    ds.setSelected(allWays);
                }
            } else {
                new Notification(
                        tr("No intersection found. Nothing was changed."))
                        .setIcon(JOptionPane.INFORMATION_MESSAGE)
                        .show();
            }
        } catch (UserCancelException exception) {
            Main.trace(exception);
            //revert changes
            //FIXME: this is dirty hack
            makeCommitsOneAction(tr("Reverting changes"));
            Main.main.undoRedo.undo();
            Main.main.undoRedo.redoCommands.clear();
        }
    }

    /**
     * Tests if the areas have some intersections to join.
     * @param areas Areas to test
     * @return {@code true} if areas are joinable
     */
    private boolean testJoin(List<Multipolygon> areas) {
        List<Way> allStartingWays = new ArrayList<>();

        for (Multipolygon area : areas) {
            allStartingWays.add(area.outerWay);
            allStartingWays.addAll(area.innerWays);
        }

        //find intersection points
        Set<Node> nodes = Geometry.addIntersections(allStartingWays, true, cmds);
        return !nodes.isEmpty();
    }

    private static class DuplicateWayCollectorAccu {
           private List<Way> currentWays = new ArrayList<>();
           private List<Way> duplicatesFound = new ArrayList<>();

           private void add(Way way) {
               List<Node> wayNodes = way.getNodes();
               List<Node> wayNodesReversed = way.getNodes();
               Collections.reverse(wayNodesReversed);
               Optional<Way> duplicate = currentWays.stream()
                   .filter(current -> current.getNodes().equals(wayNodes) || current.getNodes().equals(wayNodesReversed))
                   .findFirst();
               if (duplicate.isPresent()) {
                   currentWays.remove(duplicate.get());
                   duplicatesFound.add(duplicate.get());
                   duplicatesFound.add(way);
               } else {
                   currentWays.add(way);
               }
           }

           private DuplicateWayCollectorAccu combine(DuplicateWayCollectorAccu a2) {
               duplicatesFound.addAll(a2.duplicatesFound);
               a2.currentWays.forEach(this::add);
               return this;
           }
    }

    /**
     * A collector that collects to a list of duplicated way pairs.
     *
     * It does not scale well (O(n²)), but the data base should be small enough to make this efficient.
     *
     * @author Michael Zangl
     */
    private static class DuplicateWayCollector implements Collector<Way, DuplicateWayCollectorAccu, List<Way>> {
        @Override
        public Supplier<DuplicateWayCollectorAccu> supplier() {
            return DuplicateWayCollectorAccu::new;
        }

        @Override
        public BiConsumer<DuplicateWayCollectorAccu, Way> accumulator() {
            return DuplicateWayCollectorAccu::add;
        }

        @Override
        public BinaryOperator<DuplicateWayCollectorAccu> combiner() {
            return DuplicateWayCollectorAccu::combine;
        }

        @Override
        public Function<DuplicateWayCollectorAccu, List<Way>> finisher() {
            return a -> a.duplicatesFound;
        }

        @Override
        public Set<Collector.Characteristics> characteristics() {
            return EnumSet.of(Collector.Characteristics.UNORDERED);
        }

    }

    /**
     * Will join two or more overlapping areas
     * @param areas list of areas to join
     * @return new area formed.
     * @throws UserCancelException if user cancels the operation
     */
    public JoinAreasResult joinAreas(List<Multipolygon> areas) throws UserCancelException {

        boolean hasChanges = false;

        List<Way> allStartingWays = new ArrayList<>();
        List<Way> innerStartingWays = new ArrayList<>();
        List<Way> outerStartingWays = new ArrayList<>();

        for (Multipolygon area : areas) {
            outerStartingWays.add(area.outerWay);
            innerStartingWays.addAll(area.innerWays);
        }

        allStartingWays.addAll(innerStartingWays);
        allStartingWays.addAll(outerStartingWays);

        //first remove nodes in the same coordinate
        boolean removedDuplicates = false;
        removedDuplicates |= removeDuplicateNodes(allStartingWays);

        if (removedDuplicates) {
            hasChanges = true;
            commitCommands(marktr("Removed duplicate nodes"));
        }

        //find intersection points
        Set<Node> nodes = Geometry.addIntersections(allStartingWays, false, cmds);

        //no intersections, return.
        if (nodes.isEmpty())
            return new JoinAreasResult(hasChanges, null);
        commitCommands(marktr("Added node on all intersections"));

        List<RelationRole> relations = new ArrayList<>();

        // Remove ways from all relations so ways can be combined/split quietly
        for (Way way : allStartingWays) {
            relations.addAll(removeFromAllRelations(way));
        }

        // Don't warn now, because it will really look corrupted
        boolean warnAboutRelations = !relations.isEmpty() && allStartingWays.size() > 1;

        List<WayInPolygon> preparedWays = new ArrayList<>();

        // Split the nodes on the
        List<Way> splitOuterWays = outerStartingWays.stream()
                .flatMap(way -> splitWayOnNodes(way, nodes).stream()).collect(Collectors.toList());
        List<Way> splitInnerWays = innerStartingWays.stream()
                .flatMap(way -> splitWayOnNodes(way, nodes).stream()).collect(Collectors.toList());

        // remove duplicate ways (A->B->C and C->B->A)
        List<Way> duplicates = Stream.concat(splitOuterWays.stream(), splitInnerWays.stream()).collect(new DuplicateWayCollector());

        splitOuterWays.removeAll(duplicates);
        splitInnerWays.removeAll(duplicates);

        preparedWays.addAll(markWayInsideSide(splitOuterWays, false));
        preparedWays.addAll(markWayInsideSide(splitInnerWays, true));

        // Find boundary ways
        List<Way> discardedWays = new ArrayList<>(duplicates);
        List<AssembledPolygon> boundaries = findBoundaryPolygons(preparedWays, discardedWays);

        //find polygons
        List<AssembledMultipolygon> preparedPolygons = findPolygons(boundaries);

        //assemble final polygons
        List<Multipolygon> polygons = new ArrayList<>();
        Set<Relation> relationsToDelete = new LinkedHashSet<>();

        for (AssembledMultipolygon pol : preparedPolygons) {

            //create the new ways
            Multipolygon resultPol = joinPolygon(pol);

            //create multipolygon relation, if necessary.
            RelationRole ownMultipolygonRelation = addOwnMultipolygonRelation(resultPol.innerWays);

            //add back the original relations, merged with our new multipolygon relation
            fixRelations(relations, resultPol.outerWay, ownMultipolygonRelation, relationsToDelete);

            //strip tags from inner ways
            //TODO: preserve tags on existing inner ways
            stripTags(resultPol.innerWays);

            polygons.add(resultPol);
        }

        commitCommands(marktr("Assemble new polygons"));

        for (Relation rel: relationsToDelete) {
            cmds.add(new DeleteCommand(rel));
        }

        commitCommands(marktr("Delete relations"));

        // Delete the discarded inner ways
        if (!discardedWays.isEmpty()) {
            Command deleteCmd = DeleteCommand.delete(Main.getLayerManager().getEditLayer(), discardedWays, true);
            if (deleteCmd != null) {
                cmds.add(deleteCmd);
                commitCommands(marktr("Delete Ways that are not part of an inner multipolygon"));
            }
        }

        makeCommitsOneAction(marktr("Joined overlapping areas"));

        if (warnAboutRelations) {
            new Notification(
                    tr("Some of the ways were part of relations that have been modified.<br>Please verify no errors have been introduced."))
                    .setIcon(JOptionPane.INFORMATION_MESSAGE)
                    .setDuration(Notification.TIME_LONG)
                    .show();
        }

        return new JoinAreasResult(true, polygons);
    }

    /**
     * Checks if tags of two given ways differ, and presents the user a dialog to solve conflicts
     * @param polygons ways to check
     * @return {@code true} if all conflicts are resolved, {@code false} if conflicts remain.
     */
    private boolean resolveTagConflicts(List<Multipolygon> polygons) {

        List<Way> ways = new ArrayList<>();

        for (Multipolygon pol : polygons) {
            ways.add(pol.outerWay);
            ways.addAll(pol.innerWays);
        }

        if (ways.size() < 2) {
            return true;
        }

        TagCollection wayTags = TagCollection.unionOfAllPrimitives(ways);
        try {
            cmds.addAll(CombinePrimitiveResolverDialog.launchIfNecessary(wayTags, ways, ways));
            commitCommands(marktr("Fix tag conflicts"));
            return true;
        } catch (UserCancelException ex) {
            Main.trace(ex);
            return false;
        }
    }

    /**
     * This method removes duplicate points (if any) from the input way.
     * @param ways the ways to process
     * @return {@code true} if any changes where made
     */
    private boolean removeDuplicateNodes(List<Way> ways) {
        //TODO: maybe join nodes with JoinNodesAction, rather than reconnect the ways.

        Map<Node, Node> nodeMap = new TreeMap<>(new NodePositionComparator());
        int totalNodesRemoved = 0;

        for (Way way : ways) {
            if (way.getNodes().size() < 2) {
                continue;
            }

            int nodesRemoved = 0;
            List<Node> newNodes = new ArrayList<>();
            Node prevNode = null;

            for (Node node : way.getNodes()) {
                if (!nodeMap.containsKey(node)) {
                    //new node
                    nodeMap.put(node, node);

                    //avoid duplicate nodes
                    if (prevNode != node) {
                        newNodes.add(node);
                    } else {
                        nodesRemoved++;
                    }
                } else {
                    //node with same coordinates already exists, substitute with existing node
                    Node representator = nodeMap.get(node);

                    if (representator != node) {
                        nodesRemoved++;
                    }

                    //avoid duplicate node
                    if (prevNode != representator) {
                        newNodes.add(representator);
                    }
                }
                prevNode = node;
            }

            if (nodesRemoved > 0) {

                if (newNodes.size() == 1) { //all nodes in the same coordinate - add one more node, to have closed way.
                    newNodes.add(newNodes.get(0));
                }

                Way newWay = new Way(way);
                newWay.setNodes(newNodes);
                cmds.add(new ChangeCommand(way, newWay));
                totalNodesRemoved += nodesRemoved;
            }
        }

        return totalNodesRemoved > 0;
    }

    /**
     * Commits the command list with a description
     * @param description The description of what the commands do
     */
    private void commitCommands(String description) {
        switch(cmds.size()) {
        case 0:
            return;
        case 1:
            commitCommand(cmds.getFirst());
            break;
        default:
            commitCommand(new SequenceCommand(tr(description), cmds));
            break;
        }

        cmds.clear();
        cmdsCount++;
    }

    private static void commitCommand(Command c) {
        if (Main.main != null) {
            Main.main.undoRedo.add(c);
        } else {
            c.executeCommand();
        }
    }

    /**
     * This method analyzes the way and assigns each part what direction polygon "inside" is.
     * @param parts the split parts of the way
     * @param isInner - if true, reverts the direction (for multipolygon islands)
     * @return list of parts, marked with the inside orientation.
     * @throws IllegalArgumentException if parts is empty or not circular
     */
    private static List<WayInPolygon> markWayInsideSide(List<Way> parts, boolean isInner) {
        // the data is prepared so that all ways are split at possible intersection points.
        // To find out which side of the way the outer side is, we can follow a ray starting anywhere at the way in any direction.
        // Computation is done in East/North space.
        // We use a ray at a fixed yRay coordinate that ends at xRay;
        // we need to make sure this ray does not go into the same direction the way is going.
        // This is done by rotating by 90° if we need to.

        return parts.stream().map(way -> {
            int intersections = 0;
            // Use some random start point on the way
            EastNorth rayNode1 = way.getNode(0).getEastNorth();
            EastNorth rayNode2 = way.getNode(1).getEastNorth();
            EastNorth rayFrom = rayNode1.getCenter(rayNode2);

            // Now find the x/y mapping function. We need to ensure that rayNode1->rayNode2 is not parallel to our x axis.
            ToDoubleFunction<EastNorth> x;
            ToDoubleFunction<EastNorth> y;
            if (Math.abs(rayNode1.east() - rayNode2.east()) < Math.abs(rayNode1.north() - rayNode2.north())) {
                x = en -> en.east();
                y = en -> en.north();
            } else {
                x = en -> -en.north();
                y = en -> en.east();
            }

            double xRay = x.applyAsDouble(rayFrom);
            double yRay = y.applyAsDouble(rayFrom);

            for (Way part : parts) {
                // intersect against all way segments
                for (int i = 0; i < part.getNodesCount() - 1; i++) {
                    EastNorth n1 = part.getNode(i).getEastNorth();
                    EastNorth n2 = part.getNode(i + 1).getEastNorth();
                    if ((rayNode1.equals(n1) && rayNode2.equals(n2)) || (rayNode2.equals(n1) && rayNode1.equals(n2))) {
                        // This is the segment we are starting the ray from.
                        // We ignore this to avoid rounding errors.
                        continue;
                    }

                    double x1 = x.applyAsDouble(n1);
                    double x2 = x.applyAsDouble(n2);
                    double y1 = y.applyAsDouble(n1);
                    double y2 = y.applyAsDouble(n2);

                    if (!(y1 <= yRay && yRay < y2 || y2 <= yRay && yRay < y1)) {
                        // No intersection, since segment is above/below ray
                        continue;
                    }
                    double xIntersect = x1 + (x2 - x1) * (yRay - y1) / (y2 - y1);
                    if (xIntersect < xRay) {
                        intersections++;
                    }
                }
            }

            return new WayInPolygon(way, (intersections % 2 == 0) ^ isInner ^ (y.applyAsDouble(rayNode1) > yRay));
        }).collect(Collectors.toList());
    }

    /**
     * This is a method that splits way into smaller parts, using the prepared nodes list as split points.
     * Uses {@link SplitWayAction#splitWay} for the heavy lifting.
     * @param way way to split
     * @param nodes split points
     * @return list of split ways (or original ways if no splitting is done).
     */
    private List<Way> splitWayOnNodes(Way way, Set<Node> nodes) {

        List<Way> result = new ArrayList<>();
        List<List<Node>> chunks = buildNodeChunks(way, nodes);

        if (chunks.size() > 1) {
            SplitWayResult split = SplitWayAction.splitWay(getLayerManager().getEditLayer(), way, chunks,
                    Collections.<OsmPrimitive>emptyList(), SplitWayAction.Strategy.keepFirstChunk());

            if (split != null) {
                //execute the command, we need the results
                cmds.add(split.getCommand());
                commitCommands(marktr("Split ways into fragments"));

                result.add(split.getOriginalWay());
                result.addAll(split.getNewWays());
            }
        }
        if (result.isEmpty()) {
            //nothing to split
            result.add(way);
        }

        return result;
    }

    /**
     * Simple chunking version. Does not care about circular ways and result being
     * proper, we will glue it all back together later on.
     * @param way the way to chunk
     * @param splitNodes the places where to cut.
     * @return list of node paths to produce.
     */
    private static List<List<Node>> buildNodeChunks(Way way, Collection<Node> splitNodes) {
        List<List<Node>> result = new ArrayList<>();
        List<Node> curList = new ArrayList<>();

        for (Node node : way.getNodes()) {
            curList.add(node);
            if (curList.size() > 1 && splitNodes.contains(node)) {
                result.add(curList);
                curList = new ArrayList<>();
                curList.add(node);
            }
        }

        if (curList.size() > 1) {
            result.add(curList);
        }

        return result;
    }

    /**
     * This method finds which ways are outer and which are inner.
     * @param boundaries list of joined boundaries to search in
     * @return outer ways
     */
    private static List<AssembledMultipolygon> findPolygons(Collection<AssembledPolygon> boundaries) {

        List<PolygonLevel> list = findOuterWaysImpl(0, boundaries);
        List<AssembledMultipolygon> result = new ArrayList<>();

        //take every other level
        for (PolygonLevel pol : list) {
            if (pol.level % 2 == 0) {
                result.add(pol.pol);
            }
        }

        return result;
    }

    /**
     * Collects outer way and corresponding inner ways from all boundaries.
     * @param level depth level
     * @param boundaryWays list of joined boundaries to search in
     * @return the outermost Way.
     */
    private static List<PolygonLevel> findOuterWaysImpl(int level, Collection<AssembledPolygon> boundaryWays) {

        //TODO: bad performance for deep nestings...
        List<PolygonLevel> result = new ArrayList<>();

        for (AssembledPolygon outerWay : boundaryWays) {

            boolean outerGood = true;
            List<AssembledPolygon> innerCandidates = new ArrayList<>();

            for (AssembledPolygon innerWay : boundaryWays) {
                if (innerWay == outerWay) {
                    continue;
                }

                if (wayInsideWay(outerWay, innerWay)) {
                    outerGood = false;
                    break;
                } else if (wayInsideWay(innerWay, outerWay)) {
                    innerCandidates.add(innerWay);
                }
            }

            if (!outerGood) {
                continue;
            }

            //add new outer polygon
            AssembledMultipolygon pol = new AssembledMultipolygon(outerWay);
            PolygonLevel polLev = new PolygonLevel(pol, level);

            //process inner ways
            if (!innerCandidates.isEmpty()) {
                List<PolygonLevel> innerList = findOuterWaysImpl(level + 1, innerCandidates);
                result.addAll(innerList);

                for (PolygonLevel pl : innerList) {
                    if (pl.level == level + 1) {
                        pol.innerWays.add(pl.pol.outerWay);
                    }
                }
            }

            result.add(polLev);
        }

        return result;
    }

    /**
     * Finds all ways that form inner or outer boundaries.
     * @param multigonWays A list of (splitted) ways that form a multigon and share common end nodes on intersections.
     * @param discardedResult this list is filled with ways that are to be discarded
     * @return A list of ways that form the outer and inner boundaries of the multigon.
     */
    public static List<AssembledPolygon> findBoundaryPolygons(Collection<WayInPolygon> multigonWays,
            List<Way> discardedResult) {
        // In multigonWays collection, some way are just a point (i.e. way like nodeA-nodeA)
        // This seems to appear when is apply over invalid way like #9911 test-case
        // Remove all of these way to make the next work.
        List<WayInPolygon> cleanMultigonWays = multigonWays.stream()
                .filter(way -> way.way.getNodesCount() != 2 || !way.way.isClosed())
                .collect(Collectors.toList());
        WayTraverser traverser = new WayTraverser(cleanMultigonWays);
        List<AssembledPolygon> result = new ArrayList<>();

        try {
            WayInPolygon startWay;
            while ((startWay = traverser.startNewWay()) != null) {
                findBoundaryPolygonsStartingWith(discardedResult, traverser, result, startWay);
            }
        } catch (JosmRuntimeException | IllegalArgumentException | IllegalStateException t) {
            throw BugReport.intercept(t).put("traverser", traverser);
        }

        return fixTouchingPolygons(result);
    }

    private static void findBoundaryPolygonsStartingWith(List<Way> discardedResult, WayTraverser traverser, List<AssembledPolygon> result,
            WayInPolygon startWay) {
        List<WayInPolygon> path = new ArrayList<>();
        List<WayInPolygon> startWays = new ArrayList<>();
        try {
            path.add(startWay);
            while (true) {
                WayInPolygon leftComing = traverser.leftComingWay();
                if (leftComing != null && !startWays.contains(leftComing)) {
                    // Need restart traverser walk
                    path.clear();
                    path.add(leftComing);
                    traverser.setStartWay(leftComing);
                    startWays.add(leftComing);
                }
                WayInPolygon nextWay = traverser.walk();
                if (nextWay == null) {
                    throw new JosmRuntimeException("Join areas internal error: traverser could not find a next way.");
                }
                if (path.get(0) == nextWay) {
                    // path is closed -> stop here
                    AssembledPolygon ring = new AssembledPolygon(path);
                    if (ring.getNodes().size() <= 2) {
                        // Invalid ring (2 nodes) -> remove
                        traverser.removeWays(path);
                        for (WayInPolygon way: path) {
                            discardedResult.add(way.way);
                        }
                    } else {
                        // Close ring -> add
                        result.add(ring);
                        traverser.removeWays(path);
                    }
                    break;
                }
                if (path.contains(nextWay)) {
                    // Inner loop -> remove
                    int index = path.indexOf(nextWay);
                    while (path.size() > index) {
                        WayInPolygon currentWay = path.get(index);
                        discardedResult.add(currentWay.way);
                        traverser.removeWay(currentWay);
                        path.remove(index);
                    }
                    traverser.setStartWay(path.get(index-1));
                } else {
                    path.add(nextWay);
                }
            }
        } catch (JosmRuntimeException | IllegalArgumentException | IllegalStateException t) {
            throw BugReport.intercept(t).put("path", path);
        }
    }

    /**
     * This method checks if polygons have several touching parts and splits them in several polygons.
     * @param polygons the polygons to process.
     * @return the resulting list of polygons
     */
    public static List<AssembledPolygon> fixTouchingPolygons(List<AssembledPolygon> polygons) {
        List<AssembledPolygon> newPolygons = new ArrayList<>();

        for (AssembledPolygon ring : polygons) {
            ring.reverse();
            WayTraverser traverser = new WayTraverser(ring.ways);
            WayInPolygon startWay;

            while ((startWay = traverser.startNewWay()) != null) {
                List<WayInPolygon> simpleRingWays = new ArrayList<>();
                simpleRingWays.add(startWay);
                WayInPolygon nextWay;
                while ((nextWay = traverser.walk()) != startWay) {
                    if (nextWay == null)
                        throw new JosmRuntimeException("Join areas internal error.");
                    simpleRingWays.add(nextWay);
                }
                traverser.removeWays(simpleRingWays);
                AssembledPolygon simpleRing = new AssembledPolygon(simpleRingWays);
                simpleRing.reverse();
                newPolygons.add(simpleRing);
            }
        }

        return newPolygons;
    }

    /**
     * Tests if way is inside other way
     * @param outside outer polygon description
     * @param inside inner polygon description
     * @return {@code true} if inner is inside outer
     */
    public static boolean wayInsideWay(AssembledPolygon inside, AssembledPolygon outside) {
        Set<Node> outsideNodes = new HashSet<>(outside.getNodes());
        List<Node> insideNodes = inside.getNodes();

        for (Node insideNode : insideNodes) {

            if (!outsideNodes.contains(insideNode))
                //simply test the one node
                return Geometry.nodeInsidePolygon(insideNode, outside.getNodes());
        }

        //all nodes shared.
        return false;
    }

    /**
     * Joins the lists of ways.
     * @param polygon The list of outer ways that belong to that multipolygon.
     * @return The newly created outer way
     * @throws UserCancelException if user cancels the operation
     */
    private Multipolygon joinPolygon(AssembledMultipolygon polygon) throws UserCancelException {
        Multipolygon result = new Multipolygon(joinWays(polygon.outerWay.ways));

        for (AssembledPolygon pol : polygon.innerWays) {
            result.innerWays.add(joinWays(pol.ways));
        }

        return result;
    }

    /**
     * Joins the outer ways and deletes all short ways that can't be part of a multipolygon anyway.
     * @param ways The list of outer ways that belong to that multigon.
     * @return The newly created outer way
     * @throws UserCancelException if user cancels the operation
     */
    private Way joinWays(List<WayInPolygon> ways) throws UserCancelException {

        //leave original orientation, if all paths are reverse.
        boolean allReverse = true;
        for (WayInPolygon way : ways) {
            allReverse &= !way.insideToTheRight;
        }

        if (allReverse) {
            for (WayInPolygon way : ways) {
                way.insideToTheRight = !way.insideToTheRight;
            }
        }

        Way joinedWay = joinOrientedWays(ways);

        //should not happen
        if (joinedWay == null || !joinedWay.isClosed())
            throw new JosmRuntimeException("Join areas internal error.");

        return joinedWay;
    }

    /**
     * Joins a list of ways (using CombineWayAction and ReverseWayAction as specified in WayInPath)
     * @param ways The list of ways to join and reverse
     * @return The newly created way
     * @throws UserCancelException if user cancels the operation
     */
    private Way joinOrientedWays(List<WayInPolygon> ways) throws UserCancelException {
        if (ways.size() < 2)
            return ways.get(0).way;

        // This will turn ways so all of them point in the same direction and CombineAction won't bug
        // the user about this.

        //TODO: ReverseWay and Combine way are really slow and we use them a lot here. This slows down large joins.
        List<Way> actionWays = new ArrayList<>(ways.size());

        for (WayInPolygon way : ways) {
            actionWays.add(way.way);

            if (!way.insideToTheRight) {
                ReverseWayResult res = ReverseWayAction.reverseWay(way.way);
                commitCommand(res.getReverseCommand());
                cmdsCount++;
            }
        }

        Pair<Way, Command> result = CombineWayAction.combineWaysWorker(actionWays);

        commitCommand(result.b);
        cmdsCount++;

        return result.a;
    }

    /**
     * This method analyzes multipolygon relationships of given ways and collects addition inner ways to consider.
     * @param selectedWays the selected ways
     * @return list of polygons, or null if too complex relation encountered.
     */
    public static List<Multipolygon> collectMultipolygons(Collection<Way> selectedWays) {

        List<Multipolygon> result = new ArrayList<>();

        //prepare the lists, to minimize memory allocation.
        List<Way> outerWays = new ArrayList<>();
        List<Way> innerWays = new ArrayList<>();

        Set<Way> processedOuterWays = new LinkedHashSet<>();
        Set<Way> processedInnerWays = new LinkedHashSet<>();

        for (Relation r : OsmPrimitive.getParentRelations(selectedWays)) {
            if (r.isDeleted() || !r.isMultipolygon()) {
                continue;
            }

            boolean hasKnownOuter = false;
            outerWays.clear();
            innerWays.clear();

            for (RelationMember rm : r.getMembers()) {
                if ("outer".equalsIgnoreCase(rm.getRole())) {
                    outerWays.add(rm.getWay());
                    hasKnownOuter |= selectedWays.contains(rm.getWay());
                } else if ("inner".equalsIgnoreCase(rm.getRole())) {
                    innerWays.add(rm.getWay());
                }
            }

            if (!hasKnownOuter) {
                continue;
            }

            if (outerWays.size() > 1) {
                new Notification(
                        tr("Sorry. Cannot handle multipolygon relations with multiple outer ways."))
                        .setIcon(JOptionPane.INFORMATION_MESSAGE)
                        .show();
                return null;
            }

            Way outerWay = outerWays.get(0);

            //retain only selected inner ways
            innerWays.retainAll(selectedWays);

            if (processedOuterWays.contains(outerWay)) {
                new Notification(
                        tr("Sorry. Cannot handle way that is outer in multiple multipolygon relations."))
                        .setIcon(JOptionPane.INFORMATION_MESSAGE)
                        .show();
                return null;
            }

            if (processedInnerWays.contains(outerWay)) {
                new Notification(
                        tr("Sorry. Cannot handle way that is both inner and outer in multipolygon relations."))
                        .setIcon(JOptionPane.INFORMATION_MESSAGE)
                        .show();
                return null;
            }

            for (Way way :innerWays) {
                if (processedOuterWays.contains(way)) {
                    new Notification(
                            tr("Sorry. Cannot handle way that is both inner and outer in multipolygon relations."))
                            .setIcon(JOptionPane.INFORMATION_MESSAGE)
                            .show();
                    return null;
                }

                if (processedInnerWays.contains(way)) {
                    new Notification(
                            tr("Sorry. Cannot handle way that is inner in multiple multipolygon relations."))
                            .setIcon(JOptionPane.INFORMATION_MESSAGE)
                            .show();
                    return null;
                }
            }

            processedOuterWays.add(outerWay);
            processedInnerWays.addAll(innerWays);

            Multipolygon pol = new Multipolygon(outerWay);
            pol.innerWays.addAll(innerWays);

            result.add(pol);
        }

        //add remaining ways, not in relations
        for (Way way : selectedWays) {
            if (processedOuterWays.contains(way) || processedInnerWays.contains(way)) {
                continue;
            }

            result.add(new Multipolygon(way));
        }

        return result;
    }

    /**
     * Will add own multipolygon relation to the "previously existing" relations. Fixup is done by fixRelations
     * @param inner List of already closed inner ways
     * @return The list of relation with roles to add own relation to
     */
    private RelationRole addOwnMultipolygonRelation(Collection<Way> inner) {
        if (inner.isEmpty()) return null;
        OsmDataLayer layer = Main.getLayerManager().getEditLayer();
        // Create new multipolygon relation and add all inner ways to it
        Relation newRel = new Relation();
        newRel.put("type", "multipolygon");
        for (Way w : inner) {
            newRel.addMember(new RelationMember("inner", w));
        }
        cmds.add(layer != null ? new AddCommand(layer, newRel) :
            new AddCommand(inner.iterator().next().getDataSet(), newRel));
        addedRelations.add(newRel);

        // We don't add outer to the relation because it will be handed to fixRelations()
        // which will then do the remaining work.
        return new RelationRole(newRel, "outer");
    }

    /**
     * Removes a given OsmPrimitive from all relations.
     * @param osm Element to remove from all relations
     * @return List of relations with roles the primitives was part of
     */
    private List<RelationRole> removeFromAllRelations(OsmPrimitive osm) {
        List<RelationRole> result = new ArrayList<>();

        for (Relation r : osm.getDataSet().getRelations()) {
            if (r.isDeleted()) {
                continue;
            }
            for (RelationMember rm : r.getMembers()) {
                if (rm.getMember() != osm) {
                    continue;
                }

                Relation newRel = new Relation(r);
                List<RelationMember> members = newRel.getMembers();
                members.remove(rm);
                newRel.setMembers(members);

                cmds.add(new ChangeCommand(r, newRel));
                RelationRole saverel = new RelationRole(r, rm.getRole());
                if (!result.contains(saverel)) {
                    result.add(saverel);
                }
                break;
            }
        }

        commitCommands(marktr("Removed Element from Relations"));
        return result;
    }

    /**
     * Adds the previously removed relations again to the outer way. If there are multiple multipolygon
     * relations where the joined areas were in "outer" role a new relation is created instead with all
     * members of both. This function depends on multigon relations to be valid already, it won't fix them.
     * @param rels List of relations with roles the (original) ways were part of
     * @param outer The newly created outer area/way
     * @param ownMultipol elements to directly add as outer
     * @param relationsToDelete set of relations to delete.
     */
    private void fixRelations(List<RelationRole> rels, Way outer, RelationRole ownMultipol, Set<Relation> relationsToDelete) {
        List<RelationRole> multiouters = new ArrayList<>();

        if (ownMultipol != null) {
            multiouters.add(ownMultipol);
        }

        for (RelationRole r : rels) {
            if (r.rel.isMultipolygon() && "outer".equalsIgnoreCase(r.role)) {
                multiouters.add(r);
                continue;
            }
            // Add it back!
            Relation newRel = new Relation(r.rel);
            newRel.addMember(new RelationMember(r.role, outer));
            cmds.add(new ChangeCommand(r.rel, newRel));
        }

        OsmDataLayer layer = Main.getLayerManager().getEditLayer();
        Relation newRel;
        switch (multiouters.size()) {
        case 0:
            return;
        case 1:
            // Found only one to be part of a multipolygon relation, so just add it back as well
            newRel = new Relation(multiouters.get(0).rel);
            newRel.addMember(new RelationMember(multiouters.get(0).role, outer));
            cmds.add(new ChangeCommand(multiouters.get(0).rel, newRel));
            return;
        default:
            // Create a new relation with all previous members and (Way)outer as outer.
            newRel = new Relation();
            for (RelationRole r : multiouters) {
                // Add members
                for (RelationMember rm : r.rel.getMembers()) {
                    if (!newRel.getMembers().contains(rm)) {
                        newRel.addMember(rm);
                    }
                }
                // Add tags
                for (String key : r.rel.keySet()) {
                    newRel.put(key, r.rel.get(key));
                }
                // Delete old relation
                relationsToDelete.add(r.rel);
            }
            newRel.addMember(new RelationMember("outer", outer));
            cmds.add(layer != null ? new AddCommand(layer, newRel) : new AddCommand(outer.getDataSet(), newRel));
        }
    }

    /**
     * Remove all tags from the all the way
     * @param ways The List of Ways to remove all tags from
     */
    private void stripTags(Collection<Way> ways) {
        for (Way w : ways) {
            final Way wayWithoutTags = new Way(w);
            wayWithoutTags.removeAll();
            cmds.add(new ChangeCommand(w, wayWithoutTags));
        }
        /* I18N: current action printed in status display */
        commitCommands(marktr("Remove tags from inner ways"));
    }

    /**
     * Takes the last cmdsCount actions back and combines them into a single action
     * (for when the user wants to undo the join action)
     * @param message The commit message to display
     */
    private void makeCommitsOneAction(String message) {
        cmds.clear();
        if (Main.main != null) {
            UndoRedoHandler ur = Main.main.undoRedo;
            int i = Math.max(ur.commands.size() - cmdsCount, 0);
            for (; i < ur.commands.size(); i++) {
                cmds.add(ur.commands.get(i));
            }

            for (i = 0; i < cmds.size(); i++) {
                ur.undo();
            }
        }

        commitCommands(message == null ? marktr("Join Areas Function") : message);
        cmdsCount = 0;
    }

    @Override
    protected void updateEnabledState() {
        updateEnabledStateOnCurrentSelection();
    }

    @Override
    protected void updateEnabledState(Collection<? extends OsmPrimitive> selection) {
        setEnabled(selection != null && !selection.isEmpty());
    }
}
