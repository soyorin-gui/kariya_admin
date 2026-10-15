import { getTransactions } from '../../transaction/api';
import type { TransactionListItem } from '../../transaction/types';

export async function getAllTransactions(): Promise<TransactionListItem[]> {
  const first = await getTransactions({ pageNum: 1, pageSize: 100 });
  const pageCount = Math.ceil(first.total / first.pageSize);
  if (pageCount <= 1) return first.records;
  const remaining = await Promise.all(
    Array.from({ length: pageCount - 1 }, (_, index) => getTransactions({ pageNum: index + 2, pageSize: 100 })),
  );
  return [first, ...remaining].flatMap((page) => page.records);
}
