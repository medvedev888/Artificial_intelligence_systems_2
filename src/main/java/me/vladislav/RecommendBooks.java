package me.vladislav;

import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;

import java.io.FileInputStream;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class RecommendBooks {

    static class UserProfile {
        int age;
        List<String> genres;
    }

    static UserProfile parseInput(String input) {
        UserProfile p = new UserProfile();
        String[] parts = input.split(",", 2);
        String agePart = parts[0].replaceAll("\\D+", " ").trim();
        try {
            String num = agePart.trim().split("\\s+")[0];
            p.age = Integer.parseInt(num);
        } catch (Exception e) {
            p.age = 0;
        }
        p.genres = new ArrayList<>();
        if (parts.length > 1) {
            String likes = parts[1];
            int colon = likes.indexOf(':');
            if (colon >= 0) {
                String list = likes.substring(colon + 1).trim();
                String[] arr = list.split(",");
                for (String g : arr) {
                    p.genres.add(g.trim().toLowerCase());
                }
            }
        }
        return p;
    }

    static String genreLocalName(String genreRus) {
        Map<String, String> map = new HashMap<>();
        map.put("фэнтези", "Fantasy");
        map.put("фантастика", "ScienceFiction");
        map.put("классика", "Classic");
        map.put("детектив", "Detective");
        map.put("роман", "Romance");
        map.put("ужасы", "Horror");
        map.put("приключения", "Adventure");
        map.put("драма", "Drama");
        map.put("комедия", "Comedy");
        map.put("мистика", "Mystery");
        map.put("боевик", "Action");
        map.put("трагедия", "Tragedy");
        map.put("поэзия", "Poetry");
        return map.getOrDefault(genreRus.toLowerCase(), null);
    }

    public static void main(String[] args) throws Exception {
        String path = Paths.get("src/main/resources/books.owl").toAbsolutePath().toString();
        Model model = ModelFactory.createDefaultModel();
        try (FileInputStream in = new FileInputStream(path)) {
            model.read(in, null);
        }

        Scanner sc = new Scanner(System.in);
        System.out.println("Введите описание (например: Мне 13 лет, мне нравятся: фантастика, фэнтези). Чтобы выйти — напиши 'exit'.");

        while (true) {
            System.out.print("\n> ");
            String input = sc.nextLine().trim();

            if (input.equalsIgnoreCase("exit")) break;

            UserProfile user = parseInput(input);
            if (user.age == 0 || user.genres.isEmpty()) {
                System.out.println("Некорректный формат. Пример: Мне 13 лет, мне нравятся: фантастика, фэнтези");
                continue;
            }

            List<String> genreIds = user.genres.stream()
                    .map(RecommendBooks::genreLocalName)
                    .filter(Objects::nonNull)
                    .toList();

            if (genreIds.isEmpty()) {
                System.out.println("Не найдено сопоставления жанров. Добавь маппинг для этих жанров в код.");
                continue;
            }

            String genreFilters = genreIds.stream()
                    .map(g -> "books:" + g)
                    .collect(Collectors.joining(", "));

            String sparql = "PREFIX books: <http://ontologies/books.owl#>\n"
                    + "SELECT ?book ?title ?ageLimit ?rating WHERE {\n"
                    + "  ?book a books:Book .\n"
                    + "  OPTIONAL { ?book books:hasTitle ?title }\n"
                    + "  OPTIONAL { ?book books:hasAgeLimit ?ageLimit }\n"
                    + "  OPTIONAL { ?book books:hasRating ?rating }\n"
                    + "  ?book books:hasGenre ?g .\n"
                    + "  FILTER (?g IN (" + genreFilters + "))\n"
                    + "  FILTER (!BOUND(?ageLimit) || ?ageLimit <= " + user.age + ")\n"
                    + "}\n"
                    + "ORDER BY DESC(?rating)";

            Query query = QueryFactory.create(sparql);
            try (QueryExecution qexec = QueryExecutionFactory.create(query, model)) {
                ResultSet rs = qexec.execSelect();
                List<QuerySolution> solutions = ResultSetFormatter.toList(rs);
                if (solutions.isEmpty()) {
                    System.out.println("По вашим предпочтениям (возраст " + user.age + ", жанры " + user.genres + ") рекомендаций не найдено.");
                    continue;
                }
                System.out.println("Рекомендации книг:");
                int i = 1;
                for (QuerySolution sol : solutions) {
                    String title = sol.contains("title") ? sol.getLiteral("title").getString() : sol.getResource("book").getLocalName();
                    String ageLimit = sol.contains("ageLimit") ? sol.getLiteral("ageLimit").getString() : "не указано";
                    String rating = sol.contains("rating") ? String.valueOf(sol.getLiteral("rating").getFloat()) : "не указано";
                    System.out.printf("%d) %s (ageLimit: %s, rating: %s)\n", i++, title, ageLimit, rating);
                }
            }
        }

        System.out.println("\nРабота завершена. До встречи!");
    }
}
