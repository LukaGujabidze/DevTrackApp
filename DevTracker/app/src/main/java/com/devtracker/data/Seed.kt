package com.devtracker.data

object Seed {
    private val certMs = listOf("Review official exam guide", "Complete study material", "Take practice exams", "Schedule the exam", "Pass the exam")
    private val compMs = listOf("Study the syllabus", "Solve past papers", "Take mock contests", "Complete registration", "Participate")

    suspend fun run(dao: AppDao) {
        fun cert(t: String, p: String, ms: List<String> = certMs, url: String = "") = Triple(Goal(title = t, provider = p, type = "CERT", url = url), ms, 0)
        val goals = listOf(
            cert("PCPP1-32-101 Certificate", "Python Institute", url = "https://pythoninstitute.org/pcpp1"),
            cert("TOEFL Certificate / Cambridge C1", "ETS / Cambridge", listOf("Take a diagnostic test", "Improve vocabulary", "Practice writing & speaking", "Full mock exams", "Register", "Pass the exam")),
            cert("Goethe-Zertifikat C1", "Goethe-Institut", listOf("Take a diagnostic test", "Complete B2 to C1 grammar", "Practice all four skills", "Mock exams", "Register", "Pass the exam"), "https://www.goethe.de"),
            cert("Microsoft DP-900", "Microsoft", url = "https://learn.microsoft.com/credentials/certifications/azure-data-fundamentals/"),
            cert("Microsoft AZ-900", "Microsoft", url = "https://learn.microsoft.com/credentials/certifications/azure-fundamentals/"),
            cert("Google Cloud Professional Machine Learning Engineer", "Google Cloud",
                listOf("Complete mathematics prerequisites", "Complete machine learning fundamentals", "Study supervised learning", "Study deep learning", "Complete practical projects", "Prepare for the certification exam", "Pass the exam"),
                "https://cloud.google.com/learn/certification/machine-learning-engineer"),
            cert("AWS Certified Machine Learning Engineer – Associate", "Amazon Web Services", url = "https://aws.amazon.com/certification/"),
            cert("CPSA-F (Software Architecture Foundation)", "iSAQB", url = "https://www.isaqb.org")
        ) + listOf("International Olympiad in Informatics (IOI)", "International Olympiad in Artificial Intelligence (IOAI)",
            "International Computer Science Competition (ICSC)", "International Mathematical Olympiad (IMO)")
            .map { Triple(Goal(title = it, provider = "Competition", type = "COMP"), compMs, 0) } +
            Triple(Goal(title = "Codeforces Rating: 2100+", provider = "Codeforces", type = "CF", url = "https://codeforces.com"),
                listOf("Reach 1400", "Reach 1600", "Reach 1800", "Reach 1900", "Reach 2100"), 0)
        goals.forEach { (g, ms, _) ->
            val id = dao.insertGoal(g)
            ms.forEach { dao.insertMilestone(Milestone(parentType = "GOAL", parentId = id, title = it)) }
        }
        val courses = listOf(
            0 to listOf("Advanced Python Programming: Design Patterns, Decorators and Internals", "Data Structures and Algorithms in Python and C++", "Object-Oriented Programming, SOLID Principles and Clean Code", "Software Design Patterns: From Beginner to Advanced", "Git, GitHub and Professional Software Development Workflows"),
            1 to listOf("ASP.NET Core Web API and .NET Backend Development", "Advanced Docker: Containers, Networking and Multi-Stage Builds", "Kubernetes for Developers: Deployments, Services and Scaling", "PostgreSQL: Advanced SQL, Database Design and Optimization", "SQLAlchemy and Advanced Python Database Development", "Redis, Message Queues and Background Processing", "CI/CD with GitHub Actions and DevOps Fundamentals", "Linux Administration and Bash Scripting"),
            2 to listOf("Advanced Software Architecture and System Design", "Microservices Architecture with Python and .NET", "Domain-Driven Design and Clean Architecture", "Distributed Systems: Design, Scalability and Reliability", "Event-Driven Architecture and Message Brokers", "Cloud Architecture: AWS or Microsoft Azure"),
            3 to listOf("Mathematics for Machine Learning: Linear Algebra and Calculus", "Statistics and Probability for Data Science", "Python for Data Science: NumPy, Pandas and Matplotlib", "Machine Learning with Python and Scikit-Learn", "Deep Learning with PyTorch", "Neural Networks and Transformers: Practical Deep Learning", "Natural Language Processing with Python", "MLOps: Deploying, Monitoring and Managing ML Models"),
            4 to listOf("Large Language Models and Generative AI Engineering", "RAG Systems: Vector Databases and Semantic Search", "AI Agents and Multi-Agent Systems with Python", "Building Production AI Applications with APIs", "AI Application Deployment, Evaluation and Monitoring")
        )
        courses.forEach { (d, list) -> list.forEach { dao.insertCourse(Course(title = it, domain = d)) } }
    }
}
