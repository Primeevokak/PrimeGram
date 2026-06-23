#ifndef THREADPOOL_H
#define THREADPOOL_H

#include <vector>
#include <queue>
#include <functional>
#include <pthread.h>

class ThreadPool {
public:
    ThreadPool(size_t threads);
    ~ThreadPool();

    void enqueue(std::function<void()> task);

private:
    static void* workerThread(void* arg);

    std::vector<pthread_t> workers;
    std::queue<std::function<void()>> tasks;

    pthread_mutex_t queue_mutex;
    pthread_cond_t condition;
    bool stop;
};

#endif // THREADPOOL_H
