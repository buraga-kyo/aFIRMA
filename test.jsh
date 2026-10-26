String jsonResponse = "```json\n{\"steps\":[{\"op\":\"column-filter\",\"column\":\"titulo\",\"type\":\"contains\",\"value\":\"Contrato de prestação de serviço\"}],\"summary\":\"Filtra por Contrato de prestação de serviço no título.\"}\n```";
jsonResponse = jsonResponse.replaceAll("^```(?:json)?\\\\s*", "").replaceAll("```$", "").trim();
System.out.println(jsonResponse);
