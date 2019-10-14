package org.nd4j.autodiff.samediff.internal;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.nd4j.base.Preconditions;
import org.nd4j.linalg.primitives.Pair;

import java.util.*;

/**
 *
 * @param <T> For a dependency X -> Y, Y has type T
 * @param <D> For a dependency X -> Y, X has type D
 */
@Slf4j
public class IdentityDependencyTracker<T, D> {

    private final Queue<T> zeroDependencyQueue = new LinkedList<>();
    private final Set<T> zeroDependenciesSet = new HashSet<>();           //Same content as queue, but set for O(1) contains
    private final Map<T, Set<D>> dependencies = new IdentityHashMap<>();
    private final Map<T, Set<Pair<D,D>>> orDependencies = new IdentityHashMap<>();
    private final Map<D, Set<T>> reverseDependencies = new HashMap<>();
    private final Map<D, Set<T>> reverseOrDependencies = new HashMap<>();
    private final Set<D> satisfiedDependencies = new HashSet<>();         //Mark the dependency as satisfied. If not in set: assumed to not be satisfied

    private final Set<T> allSatisfied = Collections.newSetFromMap(new IdentityHashMap<T, Boolean>());
    private final Queue<T> allSatisfiedQueue = new LinkedList<>();

    public void clear(){
        zeroDependencyQueue.clear();
        zeroDependenciesSet.clear();
        dependencies.clear();
        orDependencies.clear();
    }

    public boolean isEmpty(){
        return zeroDependenciesSet.isEmpty() && zeroDependencyQueue.isEmpty() &&
                dependencies.isEmpty() && orDependencies.isEmpty();
    }

    public boolean isSatisfied(@NonNull D x){
        return satisfiedDependencies.contains(x);
    }

    public void markSatisfied(@NonNull D x, boolean satisfied){
        if(satisfied){
            satisfiedDependencies.add(x);
        } else {
            satisfiedDependencies.remove(x);
        }
        //TODO check all satisfied
    }

    /**
     * Check whether any dependencies x -> y exist, for y
     * @param y
     * @return
     */
    public boolean hasDependency(@NonNull T y){
        Set<D> s1 = dependencies.get(y);
        if(s1 != null && !s1.isEmpty())
            return true;

        Set<Pair<D,D>> s2 = orDependencies.get(y);
        return s2 != null && !s2.isEmpty();
    }

    /**
     * Get all dependencies x, for x -> y, and (x1 or x2) -> y
     * @param y
     * @return
     */
    public DependencyList<T,D> getDependencies(@NonNull T y){
        Set<D> s1 = dependencies.get(y);
        Set<Pair<D,D>> s2 = orDependencies.get(y);

        List<D> l1 = (s1 == null ? null : new ArrayList<>(s1));
        List<Pair<D,D>> l2 = (s2 == null ? null : new ArrayList<>(s2));

        return new DependencyList<>(y, l1, l2);
    }

    /**
     * Add a dependency: y depends on x, as in x -> y
     * @param y
     * @param x The dependency that is required for Y
     */
    public void addDependency(@NonNull T y, @NonNull D x){
        if(!dependencies.containsKey(y))
            dependencies.put(y, new HashSet<D>());

        if(!reverseDependencies.containsKey(x))
            reverseDependencies.put(x, Collections.newSetFromMap(new IdentityHashMap<T, Boolean>()));

        dependencies.get(y).add(x);
        reverseDependencies.get(x).add(y);
    }


    /**
     * Remove a dependency (x -> y)
     *
     * @param y
     * @param x The dependency that is no longer required for Y
     */
    public void removeDependency(@NonNull T y, @NonNull D x){
        if(!dependencies.containsKey(y) && !orDependencies.containsKey(y))
            return;

        Set<D> s = dependencies.get(y);
        if(s != null) {
            s.remove(x);
        }

        Set<T> s2 = reverseDependencies.get(x);
        if(s2 != null){
            s2.remove(y);
        }


        Set<Pair<D,D>> s2 = orDependencies.get(y);
        if(s2 != null) {
            boolean removedReverse = false;
            Iterator<Pair<D,D>> iter = s2.iterator();
            while(iter.hasNext()){
                Pair<D,D> p = iter.next();
                if(x.equals(p.getFirst()) || x.equals(p.getSecond())){
                    iter.remove();

                    if(!removedReverse) {
                        reverseOrDependencies.get(p.getFirst()).remove(y);
                        reverseOrDependencies.get(p.getSecond()).remove(y);
                        removedReverse = true;
                    }
                }
            }
        }





        //Add to the zero dependency queue/set
        if((s == null || s.isEmpty()) && (s2 == null || s2.isEmpty())){
            if(!zeroDependenciesSet.contains(y)){
                zeroDependenciesSet.add(y);
                zeroDependencyQueue.add(y);
            }
            dependencies.remove(y);
            orDependencies.remove(y);
        }
    }

    /**
     * Add an "Or" dependency: y requires either x1 OR x2
     *
     * @param y
     * @param x1
     * @param x2
     */
    public void addOrDependency(@NonNull T y, @NonNull D x1, @NonNull D x2){
        if(!orDependencies.containsKey(y))
            orDependencies.put(y, new HashSet<Pair<D,D>>());

        if(!reverseOrDependencies.containsKey(x1))
            reverseDependencies.put(x1, Collections.newSetFromMap(new IdentityHashMap<T, Boolean>()));
        if(!reverseOrDependencies.containsKey(x2))
            reverseDependencies.put(x2, Collections.newSetFromMap(new IdentityHashMap<T, Boolean>()));

        orDependencies.get(y).add(new Pair<>(x1, x2));
        reverseOrDependencies.get(x1).add(y);
        reverseOrDependencies.get(x2).add(y);
    }


    public boolean hasNewAllSatisfied(){
        return !allSatisfiedQueue.isEmpty();
    }

    public T getNewAllSatisfied(){
        Preconditions.checkState(hasNewAllSatisfied(), "No new/unprocessed dependents that are all satisfied");
        return allSatisfiedQueue.remove();
    }

    public List<T> getNewAllSatisfiedList(){
        Preconditions.checkState(hasNewAllSatisfied(), "No new/unprocessed dependents that are all satisfied");
        List<T> ret = new ArrayList<>(allSatisfiedQueue);
        allSatisfiedQueue.clear();
        return ret;
    }

}
