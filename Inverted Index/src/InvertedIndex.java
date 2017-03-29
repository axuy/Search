import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.util.*;

public class InvertedIndex {

    private Map<String, Map<JSONObject, List<Integer>>> index;
    private Set<JSONObject> collection;

    private InvertedIndex() {
        index = new HashMap<>();
        collection = new HashSet<>();
    }

    private Set<JSONObject> getCollection() {
        return collection;
    }

    private void build(JSONObject o) {
        JSONArray corpus = (JSONArray) o.get("corpus");

        for (int i = 0; i < corpus.size(); i++) { //iterate through all the scenes in the input file
            JSONObject currentScene = (JSONObject) corpus.get(i);
            collection.add(currentScene);
            String text = (String) currentScene.get("text");
            String[] terms = text.split("\\s+");

            for (int j = 0; j < terms.length; j++) { //iterate through every term in the current scene
                if(!index.containsKey(terms[j])) {
                    HashMap<JSONObject, List<Integer>> postings = new HashMap<>();
                    ArrayList<Integer> positions = new ArrayList<>();
                    positions.add(j);
                    postings.put(currentScene, positions);
                    index.put(terms[j], postings);
                }
                else {
                    if(!index.get(terms[j]).containsKey(currentScene)) {
                        ArrayList<Integer> positions = new ArrayList<>();
                        positions.add(j);
                        index.get(terms[j]).put(currentScene, positions);
                    }
                    else {
                        index.get(terms[j]).get(currentScene).add(j);
                    }
                }
            }
        }
    }

    private Set<JSONObject> query(String query, boolean searchAsPhrase) {
        if(query.equals("")) {
            return new HashSet<>();
        }
        long startTime = System.nanoTime();
        String[] queryTerms = query.split("\\s+");
        Set<JSONObject> candidates = null;

        for (String term : queryTerms) {
            if (candidates == null) {
                if(!index.containsKey(term)) {
                    candidates = new HashSet<>();
                } else {
                    candidates = index.get(term).keySet();
                }
            } else {
                Set<JSONObject> currentPostings = new HashSet<>();
                if(index.containsKey(term)) {
                    currentPostings = index.get(term).keySet();
                }
                Set<JSONObject> newCandidates = new HashSet<>();

                //check for which documents contain all query terms
                for (JSONObject currentCandidate : candidates) {
                    if (currentPostings.contains(currentCandidate)) {
                        newCandidates.add(currentCandidate);
                    }
                }
                candidates = newCandidates;
            }
        }
        if(!searchAsPhrase) {
            System.out.println(candidates.size() + " results for query \"" + query  + "\" (" +
                    ((System.nanoTime() - startTime) / 1000000000.0) + " seconds)");
            return candidates;
        }

        Map<JSONObject, List<Integer>> phraseCandidates = null;
        for (String term : queryTerms) {
            if (phraseCandidates == null) {
                //initializes phraseCandidates as all the scenes which contain every word in the query
                phraseCandidates = index.get(term);
                phraseCandidates.keySet().retainAll(candidates);
            } else {
                Map<JSONObject, List<Integer>> currentPostings = index.get(term);
                Map<JSONObject, List<Integer>> newPhraseCandidates = new HashMap<>();

                //eliminate candidates based on new query term
                for (Map.Entry<JSONObject, List<Integer>> currentScene: phraseCandidates.entrySet()) {
                    List<Integer> previousWordPositions = currentScene.getValue();
                    List<Integer> currentWordPositions = currentPostings.get(currentScene.getKey());

                    //check if the current word is next to the previous
                    List<Integer> newPositions = new ArrayList<>();
                    for (Integer previousPosition : previousWordPositions) {
                        if (currentWordPositions.contains(previousPosition + 1)) {
                            newPositions.add(previousPosition + 1);
                        }
                    }
                    if(newPositions.size() > 0) {
                        newPhraseCandidates.put(currentScene.getKey(), newPositions);
                    }
                }
                phraseCandidates = newPhraseCandidates;
            }
        }
        System.out.println(phraseCandidates.size() + " results for query \"" + query  + "\" (" +
                ((System.nanoTime() - startTime) / 1000000000.0) + " seconds)");
        return phraseCandidates.keySet();
    }

    private static int countOccurrences(JSONObject o, String s) {
        String newText = ((String)o.get("text")).replaceAll(s, "");
        return (((String) o.get("text")).length() - newText.length()) / s.length();
    }

    public static void main(String[] args) {
        JSONObject o = null;
        try {
            JSONParser parser = new JSONParser();
            Object obj = parser.parse(new FileReader("in/shakespeare-scenes.json"));
            o = (JSONObject) obj;
        } catch (IOException|ParseException e) {
            e.printStackTrace();
        }

        InvertedIndex index = new InvertedIndex();
        index.build(o);

        try {
            PrintWriter pw = new PrintWriter(new File("terms0"));
            PrintWriter pw2 = new PrintWriter(new File("data.csv"));
            List<String> results = new ArrayList<>();
            double shortestSceneLength = Double.POSITIVE_INFINITY;
            double sceneLengthTotal = 0.0;
            JSONObject shortestScene = null;
            for (JSONObject item : index.getCollection()) {
                int theeCount = countOccurrences(item, "thee");
                int thouCount = countOccurrences(item, "thou");
                int youCount = countOccurrences(item, "you");

                if(theeCount > youCount || thouCount > youCount) {
                    results.add((String) item.get("sceneId"));
                }

                pw2.println(item.get("sceneNum") + "," + theeCount + "," + youCount);
                int currentSceneLength = ((String) item.get("text")).length();
                if(currentSceneLength < shortestSceneLength) {
                    shortestSceneLength = currentSceneLength;
                    shortestScene = item;
                }
                sceneLengthTotal += currentSceneLength;
            }
            System.out.println("Shortest scene length: " + shortestSceneLength + shortestScene.get("sceneId"));
            System.out.println("Average scene length: " + sceneLengthTotal/(double)index.getCollection().size());
            pw2.close();
            Collections.sort(results);
            for (String result: results) {
                pw.println(result);
            }
            pw.close();

            pw = new PrintWriter(new File("terms1.txt"));
            HashSet<String> resultSet = new HashSet<>();
            for (JSONObject result: index.query("verona", true)) {
                resultSet.add((String) result.get("sceneId"));
            }
            for (JSONObject result: index.query("rome", true)) {
                resultSet.add((String) result.get("sceneId"));
            }
            for (JSONObject result: index.query("italy", true)) {
                resultSet.add((String) result.get("sceneId"));
            }
            results = new ArrayList<>(resultSet);
            Collections.sort(results);
            for (String result: results) {
                pw.println(result);
            }
            pw.close();

            pw = new PrintWriter(new File("terms2.txt"));
            results = new ArrayList<>();
            for (JSONObject result: index.query("falstaff", true)) {
                results.add((String) result.get("playId"));
            }
            Collections.sort(results);
            for (String result: results) {
                pw.println(result);
            }
            pw.close();

            pw = new PrintWriter(new File("terms3.txt"));
            results = new ArrayList<>();
            for (JSONObject result: index.query("soldier", true)) {
                results.add((String) result.get("playId"));
            }
            Collections.sort(results);
            for (String result: results) {
                pw.println(result);
            }
            pw.close();

            pw = new PrintWriter(new File("phrase0.txt"));
            results = new ArrayList<>();
            for (JSONObject result: index.query("lady macbeth", true)) {
                results.add((String) result.get("sceneId"));
            }
            Collections.sort(results);
            for (String result: results) {
                pw.println(result);
            }
            pw.close();

            pw = new PrintWriter(new File("phrase1.txt"));
            results = new ArrayList<>();
            for (JSONObject result: index.query("a rose by any other name", true)) {
                results.add((String) result.get("sceneId"));
            }
            Collections.sort(results);
            for (String result: results) {
                pw.println(result);
            }
            pw.close();

            pw = new PrintWriter(new File("phrase2.txt"));
            results = new ArrayList<>();
            for (JSONObject result: index.query("cry havoc", true)) {
                results.add((String) result.get("sceneId"));
            }
            Collections.sort(results);
            for (String result: results) {
                pw.println(result);
            }
            pw.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
}
